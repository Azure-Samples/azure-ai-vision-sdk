"""Telemetry seam for the attestation library.

The library never talks to a telemetry SDK directly. It emits telemetry through
an injected :class:`AttestationLogger`; by default a no-op logger is installed
so the library is silent until the host wires one up via
:func:`create_attestation_service`.

The free ``track_event`` / ``track_exception`` / ``track_dependency`` functions
are thin delegators to the installed logger; internal modules import those.
Signatures match the Node / .NET logger contract.
"""

from __future__ import annotations

from typing import Any, Mapping, Optional, Protocol, runtime_checkable

TelemetryProps = Optional[Mapping[str, Any]]


@runtime_checkable
class AttestationLogger(Protocol):
    def track_event(
        self,
        name: str,
        properties: TelemetryProps = None,
        measurements: Optional[Mapping[str, float]] = None,
    ) -> None: ...

    def track_exception(self, error: Any, properties: TelemetryProps = None) -> None: ...

    def track_dependency(
        self,
        name: str,
        target: str = "",
        data: str = "",
        duration: float = 0,
        success: bool = True,
        result_code: Any = None,
        dependency_type_name: str = "HTTP",
        properties: TelemetryProps = None,
    ) -> None: ...


class _NullLogger:
    def track_event(self, *args: Any, **kwargs: Any) -> None:  # noqa: D401
        pass

    def track_exception(self, *args: Any, **kwargs: Any) -> None:
        pass

    def track_dependency(self, *args: Any, **kwargs: Any) -> None:
        pass


_logger: AttestationLogger = _NullLogger()


def set_attestation_logger(logger: AttestationLogger) -> None:
    """Install the host logger. Called by ``create_attestation_service``."""
    global _logger
    _logger = logger


def track_event(
    name: str,
    properties: TelemetryProps = None,
    measurements: Optional[Mapping[str, float]] = None,
) -> None:
    _logger.track_event(name, properties, measurements)


def track_exception(error: Any, properties: TelemetryProps = None) -> None:
    _logger.track_exception(error, properties)


def track_dependency(
    name: str,
    target: str = "",
    data: str = "",
    duration: float = 0,
    success: bool = True,
    result_code: Any = None,
    dependency_type_name: str = "HTTP",
    properties: TelemetryProps = None,
) -> None:
    _logger.track_dependency(
        name,
        target,
        data,
        duration,
        success,
        result_code,
        dependency_type_name,
        properties,
    )
