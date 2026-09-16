"""Framework-agnostic per-route failure telemetry.

Only the structured failure event lives in the library. The FastAPI
request-wrapping decorator (``with_api_telemetry``) is host glue and lives in the
sample, not here.
"""

from __future__ import annotations

from typing import Any, Optional

from .logging import track_event


def track_api_fail(
    route: str,
    reason: str,
    status: int,
    properties: Optional[dict[str, Any]] = None,
) -> None:
    props: dict[str, Any] = {"route": route, "reason": reason, "status": status}
    if properties:
        props.update(properties)
    track_event("Api.Fail", props)
