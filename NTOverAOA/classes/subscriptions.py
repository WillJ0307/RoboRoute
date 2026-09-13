import threading
import time


class SubscriptionState:
    def __init__(self):
        self._lock = threading.Lock()
        self._subscribed: set = set()
        self._pending_initial = []
        self._info = {}

    @staticmethod
    def normalize_key(key):
        key = str(key).strip()
        if not key:
            return ""
        key = "/".join(part for part in key.split("/") if part)
        return "/" + key

    def add(self, keys):
        normalized = [self.normalize_key(key) for key in keys]
        normalized = [key for key in normalized if key]
        normalized = list(dict.fromkeys(normalized))

        with self._lock:
            current = self._subscribed or set()
            new_keys = [key for key in normalized if key not in current]
            self._subscribed = current | set(normalized)

            for key in normalized:
                self._info.setdefault(key, {"value": "", "time": None})

            if new_keys:
                self._pending_initial.extend(new_keys)

            return new_keys

    def subscribed(self):
        with self._lock:
            return set(self._subscribed)

    def take_initial(self):
        with self._lock:
            keys = self._pending_initial
            self._pending_initial = []
            return keys

    def requeue_initial(self, keys):
        if not keys:
            return
        with self._lock:
            self._pending_initial.extend(keys)

    def note_value(self, key, value):
        display = self._short_value(value)
        with self._lock:
            info = self._info.get(key)
            if info is not None and info["value"] != display:
                info["value"] = display
                info["time"] = time.time()
                return dict(info)
        return None

    def clear(self):
        with self._lock:
            self._subscribed = set()
            self._pending_initial.clear()
            self._info.clear()

    @staticmethod
    def _short_value(value):
        try:
            import json

            text = json.dumps(value)
        except (TypeError, ValueError):
            text = str(value)

        text = text.replace("\n", " ")
        return text if len(text) <= 80 else text[:77] + "..."
