"""POST /api/attestation/register — first-run attestation: verify the device
attestation, persist the auth cert, and return the server EC public key."""

from __future__ import annotations

import json
import re
from dataclasses import dataclass
from typing import Any, Optional, TypedDict, Union

from ..api_telemetry import track_api_fail
from ..auth_verification import verify_auth_by_system
from ..cert_store import save_certificate
from ..cert_utils import (
    compute_cert_thumbprint,
    extract_public_key_from_cert,
    validate_certificate,
    validate_certificate_expiration,
)
from ..config import get_attestation_config
from ..crypto_utils import generate_server_key_pair_ec, verify_signature_ec
from ..server_utils import get_session_data, update_session_data
from ..store import ClusterStore, StorageError
from .types import ErrorBody, HandlerOutcome, fail, json_result

ROUTE = "attestation/register"

_UUID_RE = re.compile(r"^[\da-f]{8}-([\da-f]{4}-){3}[\da-f]{12}$", re.IGNORECASE)


class AttestationRegisterBody(TypedDict, total=False):
    payload: str
    authPublicCert: str
    signature: str


@dataclass
class AttestationRegisterRequest:
    session_id: Optional[str]
    client_id: Optional[str]
    system: Optional[str]
    body: Optional[AttestationRegisterBody]


class AttestationRegisterData(TypedDict, total=False):
    platform: str
    integrityVerdict: Any
    appAttestVerdict: Any


AttestationRegisterResponse = Union[dict, ErrorBody]


async def handle_register(
    req: AttestationRegisterRequest, store: ClusterStore
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
        attest_json = parsed_payload.get("attestJson")
        if not challenge_hash or not encryption_public_cert or not attest_json:
            return fail(
                ROUTE, 400, "MISSING_PAYLOAD_FIELDS",
                "Payload must contain challengeHash, encryptionPublicCert, and attestJson",
                properties={"sid": session_id},
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

        if not json_body.get("challengeHash") or not json_body.get("clientId") or not json_body.get("system"):
            return fail(
                ROUTE, 409, "SESSION_NOT_INITIALIZED",
                "Session not initialized. Call /api/attestation/challenge first", properties={"sid": session_id},
            )

        stored_challenge = json_body["challengeHash"]
        if challenge_hash != stored_challenge:
            return fail(ROUTE, 401, "CHALLENGE_HASH_MISMATCH", "Invalid challenge hash", properties={"sid": session_id})

        if client_id.strip() != json_body.get("clientId") or system_lower != json_body.get("system"):
            return fail(
                ROUTE, 401, "CLIENT_OR_SYSTEM_MISMATCH", "Client ID or system mismatch",
                properties={
                    "sid": session_id, "requestClientId": client_id.strip(),
                    "sessionClientId": json_body.get("clientId"), "requestSystem": system_lower,
                    "sessionSystem": json_body.get("system"),
                },
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

        cert_validation = validate_certificate(auth_public_cert)
        if not cert_validation or not cert_validation.get("valid"):
            return fail(ROUTE, 400, "AUTH_CERT_STRUCTURE_INVALID", "Invalid authentication certificate", properties={"sid": session_id})

        auth_public_key = extract_public_key_from_cert(auth_public_cert)
        if not auth_public_key:
            return fail(ROUTE, 500, "AUTH_PUBKEY_EXTRACT_FAIL", "Failed to extract public key from authentication certificate", properties={"sid": session_id})

        if not verify_signature_ec(payload, signature, auth_public_key):
            return fail(ROUTE, 401, "PAYLOAD_SIGNATURE_INVALID", "Invalid signature", properties={"sid": session_id})

        message_data = {
            "challengeHash": json_body["challengeHash"],
            "clientId": json_body["clientId"],
            "system": json_body["system"],
            "publicCert": auth_public_cert,
        }
        verification_result = await verify_auth_by_system(message_data, attest_json)
        if not verification_result.get("verified"):
            return fail(
                ROUTE, 401, "ATTESTATION_VERIFICATION_FAIL", "Attestation verification failed",
                properties={
                    "sid": session_id, "platform": verification_result.get("platform"),
                    "verificationMessage": verification_result.get("message"),
                },
            )

        thumbprint = compute_cert_thumbprint(auth_public_cert)
        if not thumbprint:
            return fail(ROUTE, 500, "THUMBPRINT_COMPUTE_FAIL", "Failed to compute certificate thumbprint", properties={"sid": session_id})

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

        save_result = await save_certificate(
            store, client_id.strip(), system_lower, auth_public_cert,
            metadata={
                "verificationTimestamp": verification_result.get("timestamp"),
                "attestJson": attest_json,
                "integrityVerdict": verification_result.get("integrityVerdict"),
                "integrityVerdictPassed": verification_result.get("integrityVerdictPassed"),
                "appAttestVerdict": verification_result.get("appAttestVerdict"),
            },
        )
        if not save_result:
            return fail(ROUTE, 409, "SAVE_CERT_FAIL", "Certificate changed or could not be created")

        json_body["serverKeyGenerated"] = True
        json_body["serverEncryptionPrivateKey"] = server_priv_pem
        json_body["serverEncryptionPublicKey"] = server_pub_pem
        json_body["clientAuthPublicKey"] = auth_public_key
        json_body["clientEncryptionPublicKey"] = client_encryption_public_key
        json_body["certRegistered"] = True
        json_body["clientAuthCertThumbprint"] = save_result["thumbprint"]

        updated = await update_session_data(store, session_id, record.token, json_body, record.version)
        if not updated:
            return fail(ROUTE, 500, "UPDATE_SESSION_FAIL", "Failed to update session with server keys", properties={"sid": session_id})

        data: AttestationRegisterData = {"platform": verification_result.get("platform")}
        if verification_result.get("integrityVerdict") is not None:
            data["integrityVerdict"] = verification_result.get("integrityVerdict")
        if verification_result.get("appAttestVerdict") is not None:
            data["appAttestVerdict"] = verification_result.get("appAttestVerdict")

        return json_result(
            {"message": "Certificate stored successfully", "serverEncryptionPublicKey": server_pub_pem},
            status=200, data=data,
        )
    except StorageError:
        raise
    except Exception as err:  # noqa: BLE001
        track_api_fail(ROUTE, "INTERNAL_ERROR", 500, {"sid": session_id, "errorMessage": str(err)})
        raise
