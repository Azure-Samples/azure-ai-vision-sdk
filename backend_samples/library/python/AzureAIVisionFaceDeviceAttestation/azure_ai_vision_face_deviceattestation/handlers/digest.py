"""POST /api/liveness/digest — decrypt the client digest payload, bind + store it,
and return an encrypted acknowledgement."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Optional, TypedDict, Union

from ..api_telemetry import track_api_fail
from ..crypto_utils import (
    decrypt_with_private_key_ec,
    encrypt_with_public_key_ec,
    verify_signature_ec,
)
from ..ios_assertion_check import check_ios_assertion_for_route
from ..server_utils import get_session_data, update_session_data
from ..store import ClusterStore
from .types import ErrorBody, HandlerOutcome, fail, json_result

ROUTE = "liveness/digest"

_UUID_RE = re.compile(r"^[\da-f]{8}-([\da-f]{4}-){3}[\da-f]{12}$", re.IGNORECASE)


class LivenessDigestBody(TypedDict, total=False):
    encryptedData: str
    signature: str
    assertion: str


@dataclass
class LivenessDigestRequest:
    session_id: Optional[str]
    body: Optional[LivenessDigestBody]


LivenessDigestResponse = Union[dict, ErrorBody]


async def handle_liveness_digest(
    req: LivenessDigestRequest, store: ClusterStore
) -> HandlerOutcome:
    session_id = req.session_id
    if not session_id:
        return fail(ROUTE, 400, "MISSING_SESSION_ID", "Missing session ID")
    if not _UUID_RE.match(session_id):
        return fail(ROUTE, 400, "INVALID_SESSION_ID", "Invalid session ID format")

    body = req.body
    if body is None:
        return fail(ROUTE, 400, "INVALID_JSON_BODY", "Invalid JSON body", properties={"sid": session_id})

    encrypted_data = body.get("encryptedData")
    signature = body.get("signature")
    assertion = body.get("assertion")
    if not encrypted_data or not isinstance(encrypted_data, str):
        return fail(ROUTE, 400, "MISSING_ENCRYPTED_DATA", 'Missing or invalid "encryptedData" field (expected base64 string)', properties={"sid": session_id})
    if not signature or not isinstance(signature, str):
        return fail(ROUTE, 400, "MISSING_SIGNATURE", 'Missing or invalid "signature" field', properties={"sid": session_id})

    record = await get_session_data(store, session_id)
    if not record:
        return fail(ROUTE, 404, "SESSION_NOT_FOUND", "Session not found", properties={"sid": session_id})

    stored = record.data

    if not stored.get("serverKeyGenerated"):
        return fail(ROUTE, 409, "SERVER_KEYS_NOT_GENERATED", "Server keys not generated. Call /api/attestation/verify or /api/attestation/register first", properties={"sid": session_id})
    if not stored.get("certRegistered"):
        return fail(ROUTE, 409, "CERT_NOT_REGISTERED", "Certificate not registered. Call /api/attestation/verify or /api/attestation/register first", properties={"sid": session_id})
    if not stored.get("authCompleted"):
        return fail(ROUTE, 409, "AUTH_NOT_COMPLETED", "Obtain the session token before submitting a digest")
    if stored.get("digestCompleted"):
        return fail(ROUTE, 409, "DIGEST_ALREADY_EXISTS", "Digest already exists for this session", properties={"sid": session_id})

    client_auth_public_key = stored.get("clientAuthPublicKey")
    if not client_auth_public_key:
        return fail(ROUTE, 500, "MISSING_CLIENT_AUTH_PUBKEY", "Client auth public key not found in session", properties={"sid": session_id})

    if not verify_signature_ec(encrypted_data, signature, client_auth_public_key):
        return fail(ROUTE, 401, "SIGNATURE_INVALID", "Signature verification failed", properties={"sid": session_id})

    commit_assertion = None
    if stored.get("system") == "ios":
        thumbprint = stored.get("clientAuthCertThumbprint")
        if not thumbprint or not isinstance(thumbprint, str):
            return fail(ROUTE, 500, "MISSING_CERT_THUMBPRINT", "Client cert thumbprint not found in session", properties={"sid": session_id})
        r = await check_ios_assertion_for_route(
            store=store, route_name=ROUTE, session_id=session_id, thumbprint=thumbprint,
            blob=encrypted_data.encode("utf-8"), assertion=assertion,
        )
        if not r.ok:
            return r.result
        commit_assertion = r.commit

    server_priv = stored.get("serverEncryptionPrivateKey")
    if not server_priv:
        return fail(ROUTE, 500, "MISSING_SERVER_ENC_PRIVKEY", "Server encryption private key not found in session", properties={"sid": session_id})

    decrypted = decrypt_with_private_key_ec(encrypted_data, server_priv)
    if decrypted is None:
        return fail(ROUTE, 401, "DECRYPTION_FAIL", "Decryption failed", properties={"sid": session_id})

    try:
        message_data = json.loads(decrypted)
    except Exception:
        return fail(ROUTE, 401, "DECRYPTED_PAYLOAD_PARSE_ERROR", "Invalid decrypted payload format", properties={"sid": session_id})

    cid = message_data.get("cid")
    if not cid or not isinstance(cid, str) or cid.strip() == "":
        return fail(ROUTE, 400, "MISSING_CID", 'Missing or invalid "cid" in payload', properties={"sid": session_id})

    client_id = cid.strip()
    os_value = message_data.get("os") or ""
    digest = message_data.get("digest") or ""
    if not isinstance(digest, str) or not digest.strip():
        return fail(ROUTE, 400, "INVALID_DIGEST", "Expected a nonempty digest")

    if stored.get("clientId") and stored["clientId"] != client_id:
        return fail(
            ROUTE, 401, "CLIENT_ID_MISMATCH", "Client ID mismatch",
            properties={"sid": session_id, "expectedClientId": stored["clientId"], "actualClientId": client_id},
        )

    if stored.get("system") and stored["system"] != os_value:
        return fail(
            ROUTE, 401, "OS_MISMATCH", "Operating system mismatch",
            properties={"sid": session_id, "expectedSystem": stored["system"], "actualOs": os_value},
        )

    stored["digest"] = digest
    stored["attestationClientId"] = client_id
    stored["attestationOs"] = os_value
    stored["digestCompleted"] = True

    response_payload = {"success": True, "timestamp": datetime.now(timezone.utc).isoformat()}
    client_enc = stored.get("clientEncryptionPublicKey")
    if not client_enc:
        return fail(ROUTE, 500, "MISSING_CLIENT_ENC_PUBKEY", "Client encryption public key not found in session", properties={"sid": session_id})

    encrypted = encrypt_with_public_key_ec(json.dumps(response_payload), client_enc)
    if encrypted is None:
        return fail(ROUTE, 500, "RESPONSE_ENCRYPT_FAIL", "Failed to encrypt response", properties={"sid": session_id})

    if commit_assertion and not await commit_assertion():
        return fail(ROUTE, 409, "ASSERTION_STATE_CONFLICT", "Certificate changed or expired; obtain a fresh assertion")
    if not await update_session_data(store, session_id, record.token, stored, record.version):
        return fail(ROUTE, 409, "SESSION_STATE_CONFLICT", "Session changed or expired")
    return json_result({"encryptedData": encrypted}, status=200, data={"clientDigest": digest})
