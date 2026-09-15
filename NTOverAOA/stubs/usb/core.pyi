from collections.abc import Callable, Iterator
from typing import Any, Literal, overload

class USBError(Exception): ...
class USBTimeoutError(USBError): ...
class NoBackendError(USBError): ...

class Endpoint:
    bEndpointAddress: int
    bmAttributes: int

    def read(self, size: int = 4096, timeout: int | None = None) -> bytes: ...
    def write(self, data: Any, timeout: int | None = None) -> int: ...

class Interface:
    bInterfaceClass: int
    bInterfaceNumber: int

    def endpoints(self) -> tuple[Endpoint, ...]: ...

class Configuration:
    bConfigurationValue: int

    def interfaces(self) -> tuple[Interface, ...]: ...

class Device:
    idVendor: int
    idProduct: int
    bDeviceClass: int
    manufacturer: str | None
    product: str | None
    serial_number: str | None

    def configurations(self) -> tuple[Configuration, ...]: ...
    def get_active_configuration(self) -> Configuration: ...
    def ctrl_transfer(
        self,
        bmRequestType: int,
        bRequest: int,
        wValue: int = 0,
        wIndex: int = 0,
        data_or_wLength: Any = None,
        timeout: int | None = None,
    ) -> Any: ...

@overload
def find(
    find_all: Literal[False] = False,
    backend: Any = None,
    custom_match: Callable[[Device], bool] | None = None,
    **kwargs: Any,
) -> Device | None: ...
@overload
def find(
    find_all: Literal[True],
    backend: Any = None,
    custom_match: Callable[[Device], bool] | None = None,
    **kwargs: Any,
) -> Iterator[Device]: ...
def find(
    find_all: bool = False,
    backend: Any = None,
    custom_match: Callable[[Device], bool] | None = None,
    **kwargs: Any,
) -> Device | None | Iterator[Device]: ...
