import json
import threading
import time

import usb.core

from .nt_handler import NTHandler
from .subscriptions import SubscriptionState
from .usb_handler import USBHandler

TOPIC_RESEND_INTERVAL = 10.0


class NTOverUSBBridge:
    def __init__(self, usb=None, nt=None, on_log=None, on_state=None, on_subscription=None):
        self.usb = usb or USBHandler()
        self.nt = nt or NTHandler()
        self.subscriptions = SubscriptionState()
        self.on_log = on_log or (lambda _message: None)
        self.on_state = on_state or (lambda _name, _state: None)
        self.on_subscription = on_subscription or (lambda _key, _info: None)

        self._stop = threading.Event()
        self._thread = None
        self._next_push_retry = 0.0
        self._last_topic_send = 0.0
        self._last_read_error = 0.0

    def find_options(self):
        return self.usb.find_options()

    def start(self, ip, vidpid):
        if self._thread and self._thread.is_alive():
            return

        self._stop.clear()
        self._thread = threading.Thread(
            target=self._run,
            args=(ip, vidpid),
            daemon=True,
        )
        self._thread.start()

    def stop(self, wait=True):
        self._stop.set()
        if wait and self._thread and self._thread is not threading.current_thread():
            self._thread.join(timeout=2)
        self._thread = None

    def _run(self, ip, vidpid):
        self._state("usb", "connecting")
        self._state("nt", "connecting")

        try:
            self.usb.connect(vidpid)
            self._state("usb", "connected")
            self.nt.connect(ip, timeout=10)
            self._state("nt", "connected")
            self._send_topic_listing()
            self._last_topic_send = time.monotonic()

            while not self._stop.is_set():
                self._process_usb_message()
                self._process_nt_events()
                self._process_initial_values()
                if (
                    not self.subscriptions.subscribed()
                    and time.monotonic() - self._last_topic_send
                    >= TOPIC_RESEND_INTERVAL
                ):
                    self._send_topic_listing()
                    self._last_topic_send = time.monotonic()
                time.sleep(0.02)
        except TimeoutError as error:
            self._log(f"Connection timed out: {error}")
        except (OSError, RuntimeError, usb.core.USBError) as error:
            if not self._stop.is_set():
                self._log(f"Connection failed: {self.usb.error_hint(error)}")
        except Exception as error:  # noqa: BLE001 - worker boundary must keep the UI alive
            if not self._stop.is_set():
                self._log(f"Connection failed: {error}")
        finally:
            self._stop.set()
            self._disconnect()

    def _process_usb_message(self):
        if not self.usb.is_connected():
            return

        try:
            command = self.usb.receive_message()
        except usb.core.USBTimeoutError:
            return
        except (OSError, RuntimeError, usb.core.USBError) as error:
            now = time.monotonic()
            if now - self._last_read_error > 3:
                self._log(f"USB read error: {self.usb.error_hint(error)}")
                self._last_read_error = now
            self._stop.set()
            return
        except (TypeError, ValueError, json.JSONDecodeError) as error:
            self._log(f"Bad USB message: {error}")
            return

        if command is None:
            return

        action = command["action"]
        if action == "subscribe":
            keys = command["keys"]
            new_keys = self.subscriptions.add(keys)
            self.nt.subscribe_topics(new_keys)
            for key in new_keys:
                self.on_subscription(key, {"value": "", "time": None})
            self._log(f"Subscribed to {len(keys)} topics")
        elif action == "put":
            self.nt.put_message(command)
        else:
            self._log(f"Unknown USB action: {action}")

    def _process_nt_events(self):
        for event in self.nt.read_events():
            try:
                message = self.nt.handle_event(
                    event,
                    subscribed=self.subscriptions.subscribed(),
                )
            except Exception as error:  # noqa: BLE001 - one bad schema must not stop the bridge
                self._log(f"NetworkTables event error: {error}")
                continue

            if not message:
                continue

            try:
                parsed = json.loads(message)
                key = parsed.get("key", "")
                info = self.subscriptions.note_value(key, parsed.get("value"))
                if info is not None:
                    self.on_subscription(key, info)
            except (TypeError, ValueError, json.JSONDecodeError):
                pass

            self._send_messages([message.encode("utf-8")])

    def _process_initial_values(self):
        now = time.monotonic()
        if now < self._next_push_retry:
            return

        keys = self.subscriptions.take_initial()
        if not keys:
            return

        messages, waiting = self.nt.build_current_value_messages(keys)
        if messages:
            self._send_messages(messages)
        self.subscriptions.requeue_initial(waiting)
        self._next_push_retry = now + 0.5

    def _send_topic_listing(self):
        topics = self.nt.get_topics()
        message = (json.dumps({"topics": topics}) + "\n").encode("utf-8")
        self._send_messages([message])
        self._log(f"Sent {len(topics)} topic names")

    def _send_messages(self, messages):
        try:
            self.usb.send_messages(messages)
        except usb.core.USBTimeoutError:
            return
        except (OSError, RuntimeError, usb.core.USBError) as error:
            self._log(f"USB write error: {self.usb.error_hint(error)}")
            self._stop.set()

    def _disconnect(self):
        try:
            self.usb.disconnect()
        except Exception as error:  # noqa: BLE001 - cleanup must continue
            self._log(f"USB disconnect failed: {error}")
        try:
            self.nt.disconnect()
        except Exception as error:  # noqa: BLE001 - cleanup must continue
            self._log(f"NT disconnect failed: {error}")

        self.subscriptions.clear()
        self.on_subscription(None, None)
        self._state("usb", "disconnected")
        self._state("nt", "disconnected")

    def _log(self, message):
        self.on_log(str(message))

    def _state(self, name, state):
        self.on_state(name, state)
