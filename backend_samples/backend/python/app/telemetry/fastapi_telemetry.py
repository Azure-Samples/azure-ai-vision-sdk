"""FastAPI request-telemetry decorator (host glue).

Wraps an async route handler: emits Api.Start / Api.Success / Api.HttpFail /
Api.Fail events + a request telemetry item, and converts an unhandled exception
into a 500 response. The library only emits the structured failure event
(``track_api_fail``); this Framework-coupled piece stays in the sample.
"""

from __future__ import annotations

import functools
import time
from typing import Any, Callable

from fastapi import Request
from fastapi.responses import JSONResponse

from .app_insights_server import track_event, track_exception, track_request


def track_api_fail(route: str, reason: str, status: int, properties: dict | None = None) -> None:
    """App-level validation-failure event (mirrors the library's internal one)."""
    props: dict = {"route": route, "reason": reason, "status": status}
    if properties:
        props.update(properties)
    track_event("Api.Fail", props)


def with_api_telemetry(route: str):
    def decorator(handler: Callable[..., Any]):
        @functools.wraps(handler)
        async def wrapper(request: Request, *args: Any, **kwargs: Any):
            start_ms = time.time() * 1000
            method = request.method or "POST"
            url = f"{request.url.path}{('?' + request.url.query) if request.url.query else ''}"
            track_event("Api.Start", {"route": route, "method": method, "url": url})

            try:
                response = await handler(request, *args, **kwargs)
            except Exception as err:  # noqa: BLE001
                duration = time.time() * 1000 - start_ms
                track_exception(err, {"source": f"Api.{route}", "url": url})
                track_request(
                    name=f"{method} /api/{route}", url=url, duration=duration,
                    result_code=500, success=False,
                    properties={"route": route, "method": method, "unhandledException": True},
                )
                track_event(
                    "Api.Fail",
                    {"route": route, "reason": "UNHANDLED_EXCEPTION", "status": 500, "errorMessage": str(err)},
                    {"durationMs": duration},
                )
                return JSONResponse({"message": "Internal server error"}, status_code=500)

            duration = time.time() * 1000 - start_ms
            status = getattr(response, "status_code", 200)
            success = status < 400
            track_request(
                name=f"{method} /api/{route}", url=url, duration=duration,
                result_code=status, success=success,
                properties={"route": route, "method": method},
            )
            track_event(
                "Api.Success" if success else "Api.HttpFail",
                {"route": route, "method": method, "status": status},
                {"durationMs": duration},
            )
            return response

        return wrapper

    return decorator
