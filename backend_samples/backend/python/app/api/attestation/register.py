"""POST /api/attestation/register — thin adapter over the attestation library."""

from __future__ import annotations

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse

from azure_ai_vision_face_deviceattestation import ROUTES, AttestationRegisterRequest

from ...attestation_service import get_attestation_service
from ...telemetry.fastapi_telemetry import with_api_telemetry

router = APIRouter()
ROUTE = ROUTES["register"]


@router.post("/api/attestation/register")
@with_api_telemetry(ROUTE)
async def attestation_register(request: Request):
    qp = request.query_params
    try:
        body = await request.json()
    except Exception:
        body = None
    req = AttestationRegisterRequest(
        session_id=qp.get("s"), client_id=qp.get("cid"), system=qp.get("sys"), body=body
    )
    outcome = await get_attestation_service().register(req)
    return JSONResponse(outcome.body, status_code=outcome.status)
