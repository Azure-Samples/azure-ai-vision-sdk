"""POST /api/attestation/challenge — issue a session-bound App Attest / Key
Attestation challenge."""

from __future__ import annotations

import hashlib
import re
import secrets
from dataclasses import dataclass
from typing import Optional, Union

from ..server_utils import get_session_data, update_session_data
from ..store import ClusterStore
from .types import ErrorBody, HandlerOutcome, fail, json_result

ROUTE = "attestation/challenge"

_UUID_RE = re.compile(r"^[\da-f]{8}-([\da-f]{4}-){3}[\da-f]{12}$", re.IGNORECASE)


@dataclass
class AttestationChallengeRequest:
    session_id: Optional[str]
    client_id: Optional[str]
    system: Optional[str]


class AttestationChallengeSuccess(dict):
    """{'challengeHash': str, 'clientId': str, 'system': str}"""


AttestationChallengeResponse = Union[dict, ErrorBody]


async def handle_challenge(
    req: AttestationChallengeRequest, store: ClusterStore
) -> HandlerOutcome:
    session_id = req.session_id
    if not session_id:
        return fail(ROUTE, 400, "MISSING_SESSION_ID", "Missing session ID")
    if not _UUID_RE.match(session_id):
        return fail(ROUTE, 400, "INVALID_SESSION_ID", "Invalid session ID format")

    client_id = req.client_id
    if not client_id:
        return fail(ROUTE, 400, "MISSING_CLIENT_ID", "Missing client ID", properties={"sid": session_id})
    if not isinstance(client_id, str) or client_id.strip() == "":
        return fail(ROUTE, 400, "INVALID_CLIENT_ID", "Invalid client ID", properties={"sid": session_id})

    system = req.system
    if not system:
        return fail(ROUTE, 400, "MISSING_SYSTEM", "Missing system parameter", properties={"sid": session_id})
    system_lower = system.lower()
    if system_lower not in ("ios", "android"):
        return fail(
            ROUTE, 400, "INVALID_SYSTEM", 'Invalid system parameter. Must be "ios" or "android"',
            properties={"sid": session_id, "system": system_lower},
        )

    record = await get_session_data(store, session_id)
    if not record:
        return fail(ROUTE, 404, "SESSION_NOT_FOUND", "Session not found", properties={"sid": session_id})

    data = record.data
    if data.get("challengeHash"):
        return fail(
            ROUTE, 409, "CHALLENGE_ALREADY_EXISTS",
            "Challenge hash already exists for this session", properties={"sid": session_id},
        )

    random_bytes = secrets.token_bytes(32)
    challenge_hash = hashlib.sha256(random_bytes).hexdigest()

    data["challengeHash"] = challenge_hash
    data["clientId"] = client_id.strip()
    data["system"] = system_lower

    updated = await update_session_data(store, session_id, record.token, data, record.version)
    if not updated:
        return fail(ROUTE, 500, "UPDATE_SESSION_FAIL", "Failed to store challenge", properties={"sid": session_id})

    return json_result(
        {"challengeHash": challenge_hash, "clientId": client_id.strip(), "system": system_lower},
        status=200,
    )
