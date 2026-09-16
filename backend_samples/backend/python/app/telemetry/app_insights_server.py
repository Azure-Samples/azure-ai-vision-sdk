"""
Server-side Application Insights wrapper.

Wraps the Azure Monitor OpenTelemetry distro when
APPLICATIONINSIGHTS_CONNECTION_STRING is set; otherwise helpers write to the
application's console logger. The interface (trackEvent / trackException /
trackDependency / trackRequest / flush) is kept name-compatible with the Node
module so the rest of the port reads the same.
"""

from __future__ import annotations

import json
import logging
import os
import time
import traceback
from typing import Any, Mapping, Optional

log = logging.getLogger("ai")

_init_attempted = False
_logger: Optional[logging.Logger] = None
_meter = None


def _init_if_needed():
    global _init_attempted, _logger
    if _init_attempted:
        return _logger
    _init_attempted = True

    conn = os.getenv("APPLICATIONINSIGHTS_CONNECTION_STRING", "")
    if not conn:
        log.warning(
            "App Insights server SDK not configured: "
            "APPLICATIONINSIGHTS_CONNECTION_STRING is unset."
        )
        return None

    try:
        from azure.monitor.opentelemetry import configure_azure_monitor

        configure_azure_monitor(connection_string=conn)
        _logger = logging.getLogger("appinsights")
        return _logger
    except Exception as err:
        log.error("App Insights init failed: %s", err)
        _logger = None
        return None


def _stringify_props(props: Optional[Mapping[str, Any]]) -> dict[str, str]:
    if not props:
        return {}
    out: dict[str, str] = {}
    for k, v in props.items():
        if v is None:
            continue
        if isinstance(v, (str, int, float, bool)):
            out[k] = str(v)
        else:
            try:
                out[k] = json.dumps(v, default=str)
            except Exception:
                out[k] = str(v)
    return out


def track_event(
    name: str,
    properties: Optional[Mapping[str, Any]] = None,
    measurements: Optional[Mapping[str, float]] = None,
) -> None:
    c = _init_if_needed()
    payload = {"event": name, "properties": _stringify_props(properties)}
    if measurements:
        payload["measurements"] = {
            k: v for k, v in measurements.items() if isinstance(v, (int, float))
        }
    if c:
        c.info("Event %s", json.dumps(payload, default=str))
    else:
        log.info("[Attestation] Event %s", json.dumps(payload, default=str))


def track_exception(error: Any, properties: Optional[Mapping[str, Any]] = None) -> None:
    c = _init_if_needed()
    payload = {
        "exception": str(error),
        "stack": "".join(
            traceback.format_exception(type(error), error, error.__traceback__)
        )
        if isinstance(error, BaseException)
        else None,
        "properties": _stringify_props(properties),
    }
    if c:
        c.error("Exception %s", json.dumps(payload, default=str))
    else:
        log.error("[Attestation] Exception %s", json.dumps(payload, default=str))


def track_metric(name: str, value: float, properties: Optional[Mapping[str, Any]] = None) -> None:
    c = _init_if_needed()
    payload = {"name": name, "value": value, "properties": _stringify_props(properties)}
    if c:
        c.info("Metric %s", json.dumps(payload))
    else:
        log.info("[Attestation] Metric %s", json.dumps(payload))


def track_request(
    name: str,
    url: str,
    duration: float,
    result_code: Any,
    success: bool,
    source: Optional[str] = None,
    properties: Optional[Mapping[str, Any]] = None,
) -> None:
    c = _init_if_needed()
    props = dict(properties or {})
    if source is not None:
        props["source"] = source
    payload = {
        "request": name,
        "url": url,
        "durationMs": duration,
        "resultCode": str(result_code),
        "success": success,
        "properties": _stringify_props(props),
    }
    if c:
        c.info("Request %s", json.dumps(payload, default=str))
    else:
        log.info("[Attestation] Request %s", json.dumps(payload, default=str))


def track_dependency(
    name: str,
    target: str = "",
    data: str = "",
    duration: float = 0,
    success: bool = True,
    result_code: Any = None,
    dependency_type_name: str = "HTTP",
    properties: Optional[Mapping[str, Any]] = None,
) -> None:
    c = _init_if_needed()
    payload = {
        "dependency": name,
        "target": target,
        "data": data,
        "durationMs": duration,
        "success": success,
        "resultCode": str(result_code if result_code is not None else ("0" if success else "1")),
        "type": dependency_type_name,
        "properties": _stringify_props(properties),
    }
    if c:
        c.info("Dependency %s", json.dumps(payload, default=str))
    else:
        log.info("[Attestation] Dependency %s", json.dumps(payload, default=str))


def now_ms() -> int:
    return int(time.time() * 1000)


async def flush() -> None:
    pass
