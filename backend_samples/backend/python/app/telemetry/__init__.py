"""Adapt the host telemetry functions to the library's ``AttestationLogger``
protocol, using App Insights when configured and the console otherwise."""

from __future__ import annotations

from typing import Any, Mapping, Optional

from .app_insights_server import track_dependency, track_event, track_exception


class AppInsightsLogger:
    """Implements ``AttestationLogger`` over the host telemetry module."""

    def track_event(
        self,
        name: str,
        properties: Optional[Mapping[str, Any]] = None,
        measurements: Optional[Mapping[str, float]] = None,
    ) -> None:
        track_event(name, properties, measurements)

    def track_exception(self, error: Any, properties: Optional[Mapping[str, Any]] = None) -> None:
        track_exception(error, properties)

    def track_dependency(
        self,
        name: str,
        target: str = "",
        data: str = "",
        duration: float = 0,
        success: bool = True,
        result_code: Any = None,
        dependency_type_name: str = "HTTP",
        properties: Optional[Mapping[str, Any]] = None,
    ) -> None:
        track_dependency(name, target, data, duration, success, result_code, dependency_type_name, properties)


def make_attestation_logger() -> AppInsightsLogger:
    return AppInsightsLogger()
