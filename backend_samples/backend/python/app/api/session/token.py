"""POST /api/session/token — thin adapter over the attestation library."""

from __future__ import annotations

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse

from azure_ai_vision_face_deviceattestation import ROUTES, SessionTokenRequest

from ...attestation_service import get_attestation_service
from ...telemetry.fastapi_telemetry import with_api_telemetry

router = APIRouter()
ROUTE = ROUTES["session_token"]


@router.post("/api/session/token")
@with_api_telemetry(ROUTE)
async def session_token(request: Request):
    qp = request.query_params
    try:
        body = await request.json()
    except Exception:
        body = None
    req = SessionTokenRequest(session_id=qp.get("s"), body=body)
    outcome = await get_attestation_service().session_token(req)
    return JSONResponse(outcome.body, status_code=outcome.status)
