"""POST /api/liveness/digest — thin adapter over the attestation library."""

from __future__ import annotations

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse

from azure_ai_vision_face_deviceattestation import ROUTES, LivenessDigestRequest

from ...attestation_service import get_attestation_service
from ...telemetry.fastapi_telemetry import with_api_telemetry

router = APIRouter()
ROUTE = ROUTES["liveness_digest"]


@router.post("/api/liveness/digest")
@with_api_telemetry(ROUTE)
async def liveness_digest(request: Request):
    qp = request.query_params
    try:
        body = await request.json()
    except Exception:
        body = None
    req = LivenessDigestRequest(session_id=qp.get("s"), body=body)
    outcome = await get_attestation_service().liveness_digest(req)
    return JSONResponse(outcome.body, status_code=outcome.status)
