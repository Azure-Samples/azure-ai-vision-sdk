from __future__ import annotations

from starlette.datastructures import Headers
from starlette.responses import JSONResponse
from starlette.types import ASGIApp, Message, Receive, Scope, Send

MAX_VERIFY_IMAGE_BYTES = 6 * 1024 * 1024
MAX_REQUEST_BODY_BYTES = MAX_VERIFY_IMAGE_BYTES + 64 * 1024


class RequestBodyLimitMiddleware:
    def __init__(self, app: ASGIApp, max_body_bytes: int = MAX_REQUEST_BODY_BYTES):
        self.app = app
        self.max_body_bytes = max_body_bytes

    async def __call__(self, scope: Scope, receive: Receive, send: Send) -> None:
        if scope["type"] != "http":
            await self.app(scope, receive, send)
            return

        too_large = JSONResponse(
            {"status": "error", "code": "REQUEST_TOO_LARGE", "message": "Request body is too large"},
            status_code=413,
        )
        content_length = Headers(scope=scope).get("content-length")
        if content_length is not None:
            try:
                length = int(content_length)
                if length < 0:
                    raise ValueError("Negative Content-Length")
            except ValueError:
                await JSONResponse({"message": "Invalid Content-Length"}, status_code=400)(scope, receive, send)
                return
            if length > self.max_body_bytes:
                await too_large(scope, receive, send)
                return

        body = bytearray()
        while True:
            message = await receive()
            if message["type"] == "http.disconnect":
                return
            chunk = message.get("body", b"")
            if len(body) + len(chunk) > self.max_body_bytes:
                await too_large(scope, receive, send)
                return
            body.extend(chunk)
            if not message.get("more_body", False):
                break

        payload: bytes | None = bytes(body)
        del body, message, chunk

        async def replay_receive() -> Message:
            nonlocal payload
            if payload is not None:
                message = {"type": "http.request", "body": payload, "more_body": False}
                payload = None
                return message
            return await receive()

        await self.app(scope, replay_receive, send)