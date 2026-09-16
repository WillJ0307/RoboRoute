import json
import socket
import struct
import threading
from collections.abc import Callable
from contextlib import suppress

try:
    from websocket import create_connection
except Exception:  # noqa: BLE001 - websocket-client is an optional dependency
    create_connection = None

DS_HOST = "127.0.0.1"
DS_WS_URL = "ws://127.0.0.1:6768/ipws"
DS_TCP_JSON_PORT = 1742

_ROBOT_IP_KEYS = ("robotIp", "robotIP", "robot_ip", "robotIpv4")


def _extract_robot_ip(payload):
    if not isinstance(payload, dict):
        return None

    raw = None
    for key in _ROBOT_IP_KEYS:
        if key in payload:
            raw = payload[key]
            break

    if raw is None:
        return None

    if isinstance(raw, str):
        ip = raw.strip()
        if ip and ip != "0.0.0.0":
            return ip
        return None

    if isinstance(raw, int) and raw > 0:
        return socket.inet_ntoa(struct.pack(">I", raw))

    return None


def _fetch_via_websocket(timeout):
    if create_connection is None:
        return None

    ws = None
    try:
        ws = create_connection(DS_WS_URL, timeout=timeout)
        payload = ws.recv()
    except Exception:  # noqa: BLE001 - any failure means the DS websocket is unavailable
        return None
    finally:
        if ws is not None:
            with suppress(Exception):
                ws.close()

    try:
        return _extract_robot_ip(json.loads(payload))
    except (TypeError, ValueError, json.JSONDecodeError):
        return None


def _read_json_object(sock):
    decoder = json.JSONDecoder()
    buffer = ""

    while True:
        try:
            obj, _end = decoder.raw_decode(buffer)
            return obj if isinstance(obj, dict) else None
        except json.JSONDecodeError:
            pass

        try:
            chunk = sock.recv(1024)
        except (TimeoutError, OSError):
            return None

        if not chunk:
            return None

        buffer += chunk.decode("utf-8", "replace")


def _fetch_via_tcp_json(timeout):
    try:
        sock = socket.create_connection((DS_HOST, DS_TCP_JSON_PORT), timeout=timeout)
    except OSError:
        return None
    try:
        return _extract_robot_ip(_read_json_object(sock))
    finally:
        sock.close()


class DriverStationInterop:
    POLL_INTERVAL = 2.0
    FETCH_TIMEOUT = 1.0

    def __init__(self, on_update: Callable[[str], None] | None = None):
        self.on_update = on_update
        self._lock = threading.Lock()
        self._last_ip = None
        self._stop = threading.Event()
        self._thread = None

    @property
    def last_ip(self) -> str | None:
        with self._lock:
            return self._last_ip

    def start(self):
        if self._thread and self._thread.is_alive():
            return

        self._stop.clear()
        self._thread = threading.Thread(target=self._poll_loop, daemon=True)
        self._thread.start()

    def stop(self, wait=True):
        self._stop.set()
        if wait and self._thread and self._thread is not threading.current_thread():
            self._thread.join(timeout=3)
        self._thread = None

    def fetch_once(self, timeout: float = 1.0) -> str | None:
        ip = _fetch_via_websocket(timeout)
        if ip is None:
            ip = _fetch_via_tcp_json(timeout)
        return ip

    def _poll_loop(self):
        while not self._stop.wait(self.POLL_INTERVAL):
            ip = self.fetch_once(self.FETCH_TIMEOUT)
            if ip and ip != self.last_ip:
                with self._lock:
                    self._last_ip = ip
                if self.on_update is not None:
                    self.on_update(ip)
