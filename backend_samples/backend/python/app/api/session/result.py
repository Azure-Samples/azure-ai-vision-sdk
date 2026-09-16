"""GET /api/session/result — poll the Face service for a session's outcome.

The Face resource + API key live in the app's OWN session store (session/<sid>),
kept separate from the attestation library's record, so the browser never sees
those credentials and the library never sees the app's polling context.
"""

from __future__ import annotations

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse

from ...attestation_service import get_attestation_service
from ...face_liveness_api import FaceApiError, query_session_result
from ...store.app_session_store import get_app_session

router = APIRouter()


@router.get("/api/session/result")
async def session_result(request: Request):
    session_id = request.query_params.get("s", "")
    if not session_id:
        return JSONResponse({"status": "error", "message": "Missing session ID"}, status_code=400)

    app_session = await get_app_session(session_id)
    if not app_session:
        return JSONResponse({"status": "notfound"}, status_code=404)

    resource = app_session.resource
    api_key = app_session.api_key
    action = app_session.action or "detectLiveness"
    if not resource or not api_key:
        return JSONResponse({"status": "error", "message": "Session has no query credentials"}, status_code=409)

    # Ask the library whether the client has submitted its liveness digest yet.
    liveness = await get_attestation_service().get_liveness_outcome(session_id)
    if not liveness.completed:
        return JSONResponse({"status": "pending"})

    try:
        result = await query_session_result(resource, api_key, action, session_id)
    except FaceApiError as err:
        return JSONResponse({"status": "error", "message": f"Result query failed (HTTP {err.status})"}, status_code=502)
    except Exception:  # noqa: BLE001
        return JSONResponse({"status": "error", "message": "Could not reach the Face service"}, status_code=502)

    attempts = (result.get("results") or {}).get("attempts") or []
    attempt = (attempts[0].get("result") if attempts and isinstance(attempts[0], dict) else {}) or {}
    if not attempt.get("livenessDecision"):
        return JSONResponse({"status": "pending"})

    client_digest = liveness.client_digest
    service_digest = attempt.get("digest")
    if (
        not isinstance(client_digest, str)
        or not client_digest.strip()
        or not isinstance(service_digest, str)
        or client_digest != service_digest
    ):
        return JSONResponse(
            {"status": "error", "code": "DIGEST_MISMATCH", "message": "Liveness result digest validation failed"},
            status_code=409,
        )

    return JSONResponse({"status": "done", "result": result, "clientDigest": liveness.client_digest})
