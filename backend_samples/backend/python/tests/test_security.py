import asyncio
import json
from types import SimpleNamespace
from unittest.mock import AsyncMock

import httpx
import pytest
from fastapi.testclient import TestClient
from starlette.datastructures import UploadFile
from starlette.formparsers import MultiPartParser
from starlette.requests import Request
from starlette.responses import JSONResponse

from app.api.session import result as result_route
from app.main import app
from app.request_limits import MAX_REQUEST_BODY_BYTES, MAX_VERIFY_IMAGE_BYTES, RequestBodyLimitMiddleware
from app.store.app_session_store import AppSession
from app.web import index as index_route
from app.web import session_page


def test_result_page_includes_validation_error_state(monkeypatch):
    service = SimpleNamespace(session_exists=AsyncMock(return_value=True))
    monkeypatch.setattr(session_page, "get_attestation_service", lambda: service)
    with TestClient(app) as client:
        response = client.get("/result?s=11111111-1111-1111-1111-111111111111")
    assert response.status_code == 200
    assert "DIGEST_MISMATCH" in response.text
    assert "Session rejected: the client and service digests are missing or do not match." in response.text
    assert "response.ok && d.status === 'done'" in response.text


@pytest.mark.parametrize(
    "client_digest,service_digest,completed,has_decision,expected_status,expected_state",
    [
        ("client-digest", "client-digest", True, True, 200, "done"),
        ("client-digest", "other-digest", True, True, 409, "error"),
        ("client-digest", "CLIENT-DIGEST", True, True, 409, "error"),
        ("client-digest", None, True, True, 409, "error"),
        (None, "service-digest", True, True, 409, "error"),
        (None, None, True, True, 409, "error"),
        ("", "", True, True, 409, "error"),
        (" ", " ", True, True, 409, "error"),
        ("123", 123, True, True, 409, "error"),
        (123, 123, True, True, 409, "error"),
        ("client-digest", "other-digest", False, True, 200, "pending"),
        ("client-digest", None, True, False, 200, "pending"),
    ],
)
def test_result_requires_matching_digests(
    monkeypatch, client_digest, service_digest, completed, has_decision, expected_status, expected_state
):
    monkeypatch.setattr(result_route, "get_app_session", AsyncMock(return_value=AppSession(
        resource="test-resource", api_key="test-key", action="detectLiveness"
    )))
    service = SimpleNamespace(get_liveness_outcome=AsyncMock(return_value=SimpleNamespace(
        completed=completed, client_digest=client_digest
    )))
    monkeypatch.setattr(result_route, "get_attestation_service", lambda: service)
    attempt = {"livenessDecision": "real"} if has_decision else {}
    if service_digest is not None:
        attempt["digest"] = service_digest
    upstream = {"results": {"attempts": [{"result": attempt}]}}
    query = AsyncMock(return_value=upstream)
    monkeypatch.setattr(result_route, "query_session_result", query)

    with TestClient(app) as client:
        response = client.get("/api/session/result?s=11111111-1111-1111-1111-111111111111")

    assert response.status_code == expected_status
    body = response.json()
    assert body["status"] == expected_state
    assert query.await_count == (1 if completed else 0)
    if expected_state == "done":
        assert body["clientDigest"] == client_digest
        assert body["result"] == upstream
    else:
        assert "result" not in body
        assert "clientDigest" not in body
        if expected_state == "error":
            assert body["code"] == "DIGEST_MISMATCH"


@pytest.fixture
def session_creation(monkeypatch):
    session_id = "11111111-1111-1111-1111-111111111111"
    create = AsyncMock(return_value={"sessionId": session_id, "authToken": "test-token"})
    service = SimpleNamespace(save_session=AsyncMock(return_value=session_id))
    save = AsyncMock(return_value=True)
    monkeypatch.setattr(index_route, "create_session", create)
    monkeypatch.setattr(index_route, "get_attestation_service", lambda: service)
    monkeypatch.setattr(index_route, "save_app_session", save)
    return create, service.save_session, save


@pytest.mark.parametrize("image_size", [None, 0, 1024, MAX_VERIFY_IMAGE_BYTES, MAX_VERIFY_IMAGE_BYTES + 1])
def test_reference_image_limit(monkeypatch, session_creation, image_size):
    read_sizes = []
    original_read = UploadFile.read

    async def bounded_read(upload, size=-1):
        read_sizes.append(size)
        return await original_read(upload, size)

    monkeypatch.setattr(UploadFile, "read", bounded_read)
    files = {"verifyImage": ("verify.jpg", b"x" * image_size)} if image_size is not None else None
    with TestClient(app) as client:
        response = client.post("/", data={"resource": "test-resource", "apiKey": "test-key"},
                               files=files, follow_redirects=False)

    create, save_token, save_app = session_creation
    assert read_sizes == ([MAX_VERIFY_IMAGE_BYTES + 1] if image_size is not None else [])
    if image_size is not None and image_size > MAX_VERIFY_IMAGE_BYTES:
        assert response.status_code == 413
        assert "Reference image must be 6 MiB or smaller." in response.text
        for operation in session_creation:
            operation.assert_not_awaited()
    else:
        assert response.status_code == 303
        create.assert_awaited_once()
        save_token.assert_awaited_once()
        save_app.assert_awaited_once()
        assert len(create.await_args.args[3] or b"") == (image_size or 0)
        assert save_app.await_args.args[1].action == ("detectLivenessWithVerify" if image_size else "detectLiveness")


def post_chunks(chunks, headers, asgi_app=app):
    iterator = iter(chunks)
    received = 0
    messages = []

    async def receive():
        nonlocal received
        try:
            chunk = next(iterator)
        except StopIteration:
            return {"type": "http.request", "body": b"", "more_body": False}
        received += 1
        return {"type": "http.request", "body": chunk, "more_body": True}

    async def send(message):
        messages.append(message)

    scope = {
        "type": "http", "asgi": {"version": "3.0"}, "http_version": "1.1", "method": "POST",
        "scheme": "http", "path": "/", "raw_path": b"/", "query_string": b"", "root_path": "",
        "headers": headers, "client": ("test-client", 1234), "server": ("testserver", 80),
    }
    asyncio.run(asgi_app(scope, receive, send))
    status = next(message["status"] for message in messages if message["type"] == "http.response.start")
    body = b"".join(message.get("body", b"") for message in messages if message["type"] == "http.response.body")
    return status, body, received


@pytest.mark.parametrize("content_length", [None, "1", str(MAX_REQUEST_BODY_BYTES + 1)])
def test_request_limit_precedes_multipart_parsing(monkeypatch, session_creation, content_length):
    parse = AsyncMock(side_effect=AssertionError("Oversized requests must not reach the multipart parser"))
    monkeypatch.setattr(MultiPartParser, "parse", parse)
    headers = [(b"content-type", b"multipart/form-data; boundary=test-boundary")]
    if content_length is not None:
        headers.append((b"content-length", content_length.encode()))
    chunk = b"x" * (64 * 1024)
    chunks = [chunk] * (MAX_REQUEST_BODY_BYTES // len(chunk) + 2)

    status, body, received = post_chunks(chunks, headers)

    assert status == 413
    assert json.loads(body)["code"] == "REQUEST_TOO_LARGE"
    assert received < len(chunks)
    if content_length == str(MAX_REQUEST_BODY_BYTES + 1):
        assert received == 0
    parse.assert_not_awaited()
    for operation in session_creation:
        operation.assert_not_awaited()


def test_chunked_multipart_within_limit_is_accepted(session_creation):
    request = httpx.Request("POST", "http://testserver/", data={"resource": "test-resource", "apiKey": "test-key"},
                            files={"verifyImage": ("verify.jpg", b"test-image")})
    body = request.read()
    headers = [(name.lower(), value) for name, value in request.headers.raw if name.lower() != b"content-length"]
    chunks = [body[offset:offset + 17] for offset in range(0, len(body), 17)]

    status, _, received = post_chunks(chunks, headers)

    assert status == 303
    assert received == len(chunks)
    assert session_creation[0].await_args.args[3] == b"test-image"


@pytest.mark.parametrize("extra_byte", [False, True])
def test_request_limit_preserves_signed_json_at_boundary(extra_byte):
    original = b'{ "payload": "signed bytes", "signature": "test" }'
    received = []

    async def echo(scope, receive, send):
        request = Request(scope, receive)
        received.append(await request.body())
        await JSONResponse(await request.json())(scope, receive, send)

    limited_app = RequestBodyLimitMiddleware(echo, max_body_bytes=len(original))
    payload = original + (b" " if extra_byte else b"")
    chunks = [payload[offset:offset + 7] for offset in range(0, len(payload), 7)]
    status, body, _ = post_chunks(chunks, [(b"content-type", b"application/json")], limited_app)

    assert status == (413 if extra_byte else 200)
    assert received == ([] if extra_byte else [original])
    if not extra_byte:
        assert json.loads(body) == json.loads(original)