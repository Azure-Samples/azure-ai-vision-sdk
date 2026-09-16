"""POST /api/attestation/verify — re-check a previously registered cert (and, on
iOS, a fresh App Attest assertion) and hand back the server EC public key."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Optional, TypedDict, Union

from ..api_telemetry import track_api_fail
from ..cert_store import get_certificate
from ..cert_utils import (
    compute_cert_thumbprint,
    extract_public_key_from_cert,
    validate_certificate_expiration,
)
from ..config import get_attestation_config
from ..crypto_utils import generate_server_key_pair_ec, verify_signature_ec
from ..ios_assertion_check import check_ios_assertion_for_route
from ..server_utils import get_session_data, update_session_data
from ..store import ClusterStore, StorageError
from .types import ErrorBody, HandlerOutcome, fail, json_result

ROUTE = "attestation/verify"

_UUID_RE = re.compile(r"^[\da-f]{8}-([\da-f]{4}-){3}[\da-f]{12}$", re.IGNORECASE)


class AttestationVerifyBody(TypedDict, total=False):
    payload: str
    authPublicCert: str
    signature: str
    assertion: str


@dataclass
class AttestationVerifyRequest:
    session_id: Optional[str]
    client_id: Optional[str]
    system: Optional[str]
    body: Optional[AttestationVerifyBody]


AttestationVerifyResponse = Union[dict, ErrorBody]


async def handle_verify(
    req: AttestationVerifyRequest, store: ClusterStore
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

    body = req.body
    if body is None:
        return fail(ROUTE, 400, "INVALID_JSON_BODY", "Invalid JSON body", properties={"sid": session_id})

    payload = body.get("payload")
    auth_public_cert = body.get("authPublicCert")
    signature = body.get("signature")
    assertion = body.get("assertion")
    if not payload or not auth_public_cert or not signature:
        return fail(
            ROUTE, 400, "MISSING_BODY_FIELDS",
            "Missing required fields: payload, authPublicCert, signature", properties={"sid": session_id},
        )

    if not isinstance(payload, str) or payload.strip() == "":
        return fail(ROUTE, 400, "INVALID_PAYLOAD_FORMAT", "Invalid payload format. Expected JSON string", properties={"sid": session_id})

    try:
        parsed_payload = json.loads(payload)
        challenge_hash = parsed_payload.get("challengeHash")
        encryption_public_cert = parsed_payload.get("encryptionPublicCert")
        if not challenge_hash or not encryption_public_cert:
            return fail(
                ROUTE, 400, "MISSING_PAYLOAD_FIELDS",
                "Payload must contain challengeHash and encryptionPublicCert", properties={"sid": session_id},
            )
    except Exception:
        return fail(ROUTE, 400, "PAYLOAD_JSON_PARSE_ERROR", "Invalid payload JSON format", properties={"sid": session_id})

    if "-----BEGIN CERTIFICATE-----" not in auth_public_cert or "-----BEGIN CERTIFICATE-----" not in encryption_public_cert:
        return fail(ROUTE, 400, "INVALID_CERT_PEM", "Invalid certificate format. Expected PEM format", properties={"sid": session_id})

    max_cert_size = get_attestation_config().max_cert_size
    if len(auth_public_cert) > max_cert_size or len(encryption_public_cert) > max_cert_size:
        return fail(
            ROUTE, 400, "CERT_TOO_LARGE",
            f"Certificate size exceeds maximum allowed size of {max_cert_size} bytes",
            properties={
                "sid": session_id, "maxCertSize": max_cert_size,
                "authBytes": len(auth_public_cert), "encryptionBytes": len(encryption_public_cert),
            },
        )

    try:
        record = await get_session_data(store, session_id)
        if not record:
            return fail(ROUTE, 404, "SESSION_NOT_FOUND", "Session not found", properties={"sid": session_id})

        json_body = record.data

        stored_challenge = json_body.get("challengeHash")
        if not stored_challenge:
            return fail(
                ROUTE, 400, "CHALLENGE_NOT_INITIALIZED",
                "Challenge hash not found in session. Session may have expired or not been initialized.",
                properties={"sid": session_id},
            )
        if challenge_hash != stored_challenge:
            return fail(ROUTE, 401, "CHALLENGE_HASH_MISMATCH", "Invalid challenge hash", properties={"sid": session_id})

        stored_client_id = json_body.get("clientId")
        if stored_client_id and stored_client_id != client_id.strip():
            return fail(
                ROUTE, 401, "CLIENT_ID_MISMATCH", "Client ID mismatch",
                properties={"sid": session_id, "expectedClientId": stored_client_id, "actualClientId": client_id.strip()},
            )
        stored_system = json_body.get("system")
        if stored_system and stored_system != system_lower:
            return fail(
                ROUTE, 401, "SYSTEM_MISMATCH", "System mismatch",
                properties={"sid": session_id, "expectedSystem": stored_system, "actualSystem": system_lower},
            )

        auth_exp = validate_certificate_expiration(auth_public_cert)
        if auth_exp is None:
            return fail(ROUTE, 500, "AUTH_CERT_EXP_VALIDATION_FAIL", "Failed to validate authentication certificate expiration", properties={"sid": session_id})
        if auth_exp.is_expired:
            return fail(
                ROUTE, 403, "AUTH_CERT_EXPIRED", "Authentication certificate has expired",
                properties={"sid": session_id, "expiredAt": auth_exp.not_after.isoformat()},
                body={"expiredAt": auth_exp.not_after.isoformat()},
            )
        if auth_exp.is_not_yet_valid:
            return fail(
                ROUTE, 403, "AUTH_CERT_NOT_YET_VALID", "Authentication certificate is not yet valid",
                properties={"sid": session_id, "validFrom": auth_exp.not_before.isoformat()},
                body={"validFrom": auth_exp.not_before.isoformat()},
            )

        auth_public_key = extract_public_key_from_cert(auth_public_cert)
        if not auth_public_key:
            return fail(ROUTE, 500, "AUTH_PUBKEY_EXTRACT_FAIL", "Failed to extract public key from authentication certificate", properties={"sid": session_id})

        if not verify_signature_ec(payload, signature, auth_public_key):
            return fail(ROUTE, 401, "PAYLOAD_SIGNATURE_INVALID", "Invalid signature", properties={"sid": session_id})

        thumbprint = compute_cert_thumbprint(auth_public_cert)
        if not thumbprint:
            return fail(ROUTE, 500, "THUMBPRINT_COMPUTE_FAIL", "Failed to compute certificate thumbprint", properties={"sid": session_id})

        cert_snapshot = await get_certificate(store, thumbprint)
        cert_record = cert_snapshot.value if cert_snapshot else None
        if not cert_record:
            track_api_fail(
                ROUTE, "CERT_NOT_REGISTERED", 200,
                {"sid": session_id, "thumbprint": thumbprint, "clientId": client_id.strip(), "system": system_lower},
            )
            return json_result({"exists": False}, status=200)

        if cert_record.client_id != client_id.strip():
            return fail(
                ROUTE, 401, "CERT_CLIENT_ID_MISMATCH", "Certificate clientId mismatch",
                properties={"sid": session_id, "thumbprint": thumbprint, "expectedClientId": cert_record.client_id, "actualClientId": client_id.strip()},
            )
        if cert_record.system != system_lower:
            return fail(
                ROUTE, 401, "CERT_SYSTEM_MISMATCH", "Certificate system mismatch",
                properties={"sid": session_id, "thumbprint": thumbprint, "expectedSystem": cert_record.system, "actualSystem": system_lower},
            )

        commit_assertion = None
        if system_lower == "ios":
            r = await check_ios_assertion_for_route(
                store=store, route_name=ROUTE, session_id=session_id, thumbprint=thumbprint,
                blob=payload.encode("utf-8"), assertion=assertion,
            )
            if not r.ok:
                return r.result
            commit_assertion = r.commit

        if json_body.get("serverKeyGenerated"):
            return fail(ROUTE, 409, "SERVER_KEYS_ALREADY_GENERATED", "Server keys already generated for this session", properties={"sid": session_id})

        enc_exp = validate_certificate_expiration(encryption_public_cert)
        if enc_exp is None:
            return fail(ROUTE, 500, "ENC_CERT_EXP_VALIDATION_FAIL", "Failed to validate encryption certificate expiration", properties={"sid": session_id})
        if enc_exp.is_expired:
            return fail(
                ROUTE, 403, "ENC_CERT_EXPIRED", "Encryption certificate has expired",
                properties={"sid": session_id, "expiredAt": enc_exp.not_after.isoformat()},
                body={"expiredAt": enc_exp.not_after.isoformat()},
            )
        if enc_exp.is_not_yet_valid:
            return fail(
                ROUTE, 403, "ENC_CERT_NOT_YET_VALID", "Encryption certificate is not yet valid",
                properties={"sid": session_id, "validFrom": enc_exp.not_before.isoformat()},
                body={"validFrom": enc_exp.not_before.isoformat()},
            )

        client_encryption_public_key = extract_public_key_from_cert(encryption_public_cert)
        if not client_encryption_public_key:
            return fail(ROUTE, 500, "ENC_PUBKEY_EXTRACT_FAIL", "Failed to extract public key from encryption certificate", properties={"sid": session_id})

        kp = generate_server_key_pair_ec()
        if not kp:
            return fail(ROUTE, 500, "SERVER_KEYPAIR_GEN_FAIL", "Failed to generate server key pair", properties={"sid": session_id})
        server_pub_pem, server_priv_pem = kp

        json_body["serverKeyGenerated"] = True
        json_body["serverEncryptionPrivateKey"] = server_priv_pem
        json_body["serverEncryptionPublicKey"] = server_pub_pem
        json_body["clientAuthPublicKey"] = auth_public_key
        json_body["clientEncryptionPublicKey"] = client_encryption_public_key
        json_body["certRegistered"] = True
        json_body["clientAuthCertThumbprint"] = thumbprint

        if commit_assertion and not await commit_assertion():
            return fail(ROUTE, 409, "ASSERTION_STATE_CONFLICT", "Certificate changed or expired; obtain a fresh assertion")
        updated = await update_session_data(store, session_id, record.token, json_body, record.version)
        if not updated:
            return fail(ROUTE, 500, "UPDATE_SESSION_FAIL", "Failed to update session with server keys", properties={"sid": session_id})

        return json_result({"exists": True, "serverEncryptionPublicKey": server_pub_pem}, status=200)
    except StorageError:
        raise
    except Exception as err:  # noqa: BLE001
        return fail(ROUTE, 500, "STORAGE_ERROR", "Storage operation failed", properties={"sid": session_id, "errorMessage": str(err)})
