"""Result envelope returned by every service route method.

``HandlerOutcome`` splits the client-facing response (``status`` + ``body``) from
host-facing metadata (``ok``, machine ``code``, ``message``, optional ``data``).
Mirrors the Node / .NET ``HandlerOutcome`` + ``ErrorBody``.

Response ``body`` dicts use the same camelCase keys as the Node backend so the
mobile clients see an identical wire contract.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Optional, TypedDict

from ..api_telemetry import track_api_fail


class ErrorBody(TypedDict, total=False):
    message: str
    expiredAt: str
    validFrom: str


@dataclass
class HandlerOutcome:
    ok: bool
    code: str
    status: int
    body: dict[str, Any]
    message: Optional[str] = None
    data: Optional[dict[str, Any]] = None


def json_result(
    body: dict[str, Any],
    *,
    status: int = 200,
    code: Optional[str] = None,
    data: Optional[dict[str, Any]] = None,
) -> HandlerOutcome:
    """Build a success (or plain) outcome from a client-facing body."""
    ok = status < 400
    message = body.get("message") if isinstance(body, dict) else None
    return HandlerOutcome(
        ok=ok,
        code=code or ("OK" if ok else "ERROR"),
        status=status,
        body=body,
        message=message,
        data=data,
    )


def fail(
    route: str,
    status: int,
    code: str,
    message: str,
    *,
    properties: Optional[dict[str, Any]] = None,
    body: Optional[dict[str, Any]] = None,
) -> HandlerOutcome:
    """Emit failure telemetry and return the matching error outcome.

    Co-locates ``track_api_fail`` with the returned outcome so the two can't
    drift apart.
    """
    track_api_fail(route, code, status, properties)
    err_body: dict[str, Any] = {"message": message}
    if body:
        err_body.update(body)
    return HandlerOutcome(ok=False, code=code, status=status, body=err_body, message=message, data=None)
