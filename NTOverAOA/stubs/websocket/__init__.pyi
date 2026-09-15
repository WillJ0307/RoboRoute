from typing import Any

class WebSocket:
    def recv(self) -> str: ...
    def send(
        self,
        payload: str | bytes,
        opcode: int | None = None,
        mask: bool | None = None,
    ) -> int | None: ...
    def close(self, timeout: float | None = None, reason: str = "") -> None: ...
    def fileno(self) -> int: ...

def create_connection(
    url: str,
    timeout: float | None = None,
    **kwargs: Any,
) -> WebSocket: ...
