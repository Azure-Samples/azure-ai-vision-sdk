"""POST /api/attestation/challenge — thin adapter over the attestation library."""

from __future__ import annotations

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse

from azure_ai_vision_face_deviceattestation import ROUTES, AttestationChallengeRequest

from ...attestation_service import get_attestation_service
from ...telemetry.fastapi_telemetry import with_api_telemetry

router = APIRouter()
ROUTE = ROUTES["challenge"]


@router.post("/api/attestation/challenge")
@with_api_telemetry(ROUTE)
async def attestation_challenge(request: Request):
    qp = request.query_params
    req = AttestationChallengeRequest(
        session_id=qp.get("s"), client_id=qp.get("cid"), system=qp.get("sys")
    )
    outcome = await get_attestation_service().challenge(req)
    return JSONResponse(outcome.body, status_code=outcome.status)
