import base64
import json
import time

import ntcore

from StructDataStuff import SchemaRegistry


def value_to_json(v):
    value_type = v.type()
    value = v.value()

    if value_type == ntcore.NetworkTableType.kRaw or isinstance(
        value, (bytes, bytearray, memoryview)
    ):
        return base64.b64encode(bytes(value)).decode("ascii")

    try:
        json.dumps(value)
    except TypeError:
        return str(value)
    return value


class NTHandler:
    def __init__(self, client_name="NTOverAOA", registry=None):
        self.client_name = client_name

        if registry is None:
            self.registry = SchemaRegistry()
        else:
            self.registry = registry

        self.inst = None
        self.poller = None
        self.ip = None
        self.connected = False
        self._subscribers = {}

    def is_connected(self):
        if not self.connected:
            return False

        if self.inst is None:
            return False

        return self.inst.isConnected()

    def connect(self, ip, timeout=10.0, client_name=None):
        self.ip = ip

        if client_name is not None:
            self.client_name = client_name

        self.inst = ntcore.NetworkTableInstance.getDefault()
        self.inst.setServer(ip)
        self.inst.startClient4(self.client_name)

        self.poller = ntcore.NetworkTableListenerPoller(self.inst)
        self.poller.addListener([""], ntcore.EventFlags.kValueRemote)

        start_time = time.monotonic()

        while not self.inst.isConnected():
            if time.monotonic() - start_time > timeout:
                raise TimeoutError(f"Connection to {ip} timed out")

            time.sleep(0.2)

        self.connected = True

        return self.inst

    def disconnect(self):
        self.connected = False

        for subscriber in self._subscribers.values():
            close = getattr(subscriber, "close", None)
            if close is not None:
                close()
        self._subscribers.clear()

        if self.poller is not None:
            self.poller.close()

            self.poller = None

        if self.inst is not None:
            self.inst.stopClient()

            self.inst = None

    def read_events(self):
        if self.poller is None:
            return []

        return self.poller.readQueue()

    def put_message(self, msg):
        key = msg.get("key")
        value = msg.get("value")

        if key is None or value is None:
            return False

        self.inst.getTable("").putValue(key, value)

        return True

    def subscribe_topics(self, keys):
        if self.inst is None:
            return

        for key in keys:
            if key in self._subscribers:
                continue

            topic = self.inst.getTopic(key)
            if topic is not None:
                self._subscribers[key] = topic.genericSubscribe()

    def build_outgoing(self, event, table_prefix="", subscribed=None):
        data = event.data
        key = data.topic.getName()

        if table_prefix and not key.startswith(table_prefix):
            return None

        if subscribed is not None and key not in subscribed:
            return None

        type_str = data.topic.getTypeString()

        msg = {
            "key": key,
            "type": type_str,
            "time": data.value.time() / 1_000_000.0,
        }

        schema = self.registry.get_schema(type_str)
        if schema is not None:
            msg["schema"] = schema

        try:
            if self.registry.has_type(type_str):
                msg["value"] = self.registry.decode_type(
                    type_str,
                    data.value.value(),
                )
            else:
                msg["value"] = value_to_json(data.value)
        except Exception:  # noqa: BLE001 - fall back when a value cannot be serialized
            msg["value"] = value_to_json(data.value)

        return json.dumps(msg) + "\n"

    def handle_event(self, event, table_prefix="", subscribed=None):
        key = event.data.topic.getName()
        marker = "/.schema/"

        if marker in key:
            name = key.split(marker, 1)[1]

            schema = event.data.value.value()
            schema = schema.decode("utf-8", "replace")
            self.registry.register(name, schema)

            return None

        return self.build_outgoing(event, table_prefix, subscribed)

    def get_topics(self):
        if self.inst is None:
            return []

        topics = self.inst.getTopics()

        return [topic.getName() for topic in topics]

    def topic_exists(self, key):
        if self.inst is None:
            return False

        topic = self.inst.getTopic(key)

        if topic is None:
            return False

        return topic.exists()

    def topic_type(self, key):
        if self.inst is None:
            return ""

        topic = self.inst.getTopic(key)

        if topic is None:
            return ""

        return topic.getTypeString()

    def build_current_value_messages(self, keys, table_prefix=""):
        pending = []
        still_waiting = []

        for key in keys:
            try:
                if table_prefix and not key.startswith(table_prefix):
                    still_waiting.append(key)
                    continue

                if self.inst is None:
                    still_waiting.append(key)
                    continue

                topic = self.inst.getTopic(key)

                if topic is None:
                    still_waiting.append(key)
                    continue

                type_str = topic.getTypeString()
                value = None

                entry = self.inst.getEntry(key)
                current_value = entry.getValue()

                if current_value is not None and current_value.isValid():
                    value = current_value

                if value is None and topic.exists():
                    subscriber = self._subscribers.get(key)
                    if subscriber is None:
                        subscriber = topic.genericSubscribe()
                        self._subscribers[key] = subscriber
                    deadline = time.monotonic() + 0.5

                    while time.monotonic() < deadline:
                        current_value = subscriber.get()

                        if current_value is not None and current_value.isValid():
                            value = current_value
                            break

                        time.sleep(0.02)

                if value is None:
                    still_waiting.append(key)
                    continue

                msg = {
                    "key": key,
                    "type": type_str,
                    "time": value.time() / 1_000_000.0,
                }

                schema = self.registry.get_schema(type_str)
                if schema is not None:
                    msg["schema"] = schema

                try:
                    if self.registry.has_type(type_str):
                        msg["value"] = self.registry.decode_type(
                            type_str,
                            value.value(),
                        )
                    else:
                        msg["value"] = value_to_json(value)
                except Exception:  # noqa: BLE001 - fall back when a value cannot be serialized
                    msg["value"] = value_to_json(value)

                pending.append((json.dumps(msg) + "\n").encode("utf-8"))

            except Exception:  # noqa: BLE001 - leave unavailable topics for retry
                still_waiting.append(key)

        return pending, still_waiting
