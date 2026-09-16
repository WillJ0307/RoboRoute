from typing import Any

ENDPOINT_IN: int
ENDPOINT_OUT: int
ENDPOINT_DIR_MASK: int
ENDPOINT_TYPE_MASK: int
ENDPOINT_TYPE_CONTROL: int
ENDPOINT_TYPE_ISOCHRONOUS: int
ENDPOINT_TYPE_BULK: int
ENDPOINT_TYPE_INTERRUPT: int

CTRL_TYPE_STANDARD: int
CTRL_TYPE_CLASS: int
CTRL_TYPE_VENDOR: int
CTRL_TYPE_RESERVED: int
CTRL_RECIPIENT_DEVICE: int
CTRL_RECIPIENT_INTERFACE: int
CTRL_RECIPIENT_ENDPOINT: int
CTRL_RECIPIENT_OTHER: int
CTRL_IN: int
CTRL_OUT: int

def endpoint_direction(endpoint_address: int) -> int: ...
def endpoint_type(endpoint_attributes: int) -> int: ...
def dispose_resources(device_or_handle: Any) -> None: ...
