"""
Per-phase helpers for verify_ios_auth. Each phase returns its result payload
on success, or raises IosPhaseFail with reason + extras.
"""

from __future__ import annotations

import hashlib
import logging
from typing import Any

from cryptography import x509
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec

from ..cert_utils import matches_pinned_ca, pem_to_der, validate_certificate_path
from ..crypto_utils import verify_signature_ec_bytes
from .constants import (
    AAGUID_DEV,
    AAGUID_PROD,
    AAGUID_OFFSET,
    APPLE_APP_ATTEST_ROOT_CAS,
    AUTH_DATA_HEADER_BYTES,
    CREDENTIAL_ID_LENGTH_OFFSET,
    CREDENTIAL_ID_OFFSET,
)
from .parsers import (
    extract_nonce_from_cred_cert,
    parse_app_attest_assertion,
    parse_app_attest_token,
    parse_assertion_auth_data,
)
from .types import (
    AppAttestAssertionObject,
    AppAttestObject,
    IosPhaseFail,
)

log = logging.getLogger("ios.attestation_phases")


def parse_ios_attest_envelope(attest_json: str, base_props: dict) -> dict:
    """Parse outer JSON wrapper + decode CBOR attestation and assertion."""
    import json

    try:
        parsed = json.loads(attest_json)
    except Exception as e:
        raise IosPhaseFail("ATTEST_JSON_PARSE_ERROR", "Invalid attestation: failed to parse attestJson") from e

    if not isinstance(parsed.get("attestation"), str) or not parsed["attestation"]:
        raise IosPhaseFail("MISSING_ATTESTATION", "Invalid attestation: missing attestation field")
    if not isinstance(parsed.get("assertion"), str) or not parsed["assertion"]:
        raise IosPhaseFail("MISSING_ASSERTION", "Invalid attestation: missing assertion (auth-cert binding proof)")

    try:
        parsed_token: AppAttestObject = parse_app_attest_token(parsed["attestation"])
    except Exception as e:
        raise IosPhaseFail("ATTESTATION_DECODE_ERROR", f"Invalid attestation: {e}") from e

    if parsed_token.fmt != "apple-appattest":
        raise IosPhaseFail(
            "UNEXPECTED_FMT",
            f"Unexpected fmt: {parsed_token.fmt}",
            extra_props={"fmt": parsed_token.fmt},
        )

    try:
        parsed_assertion: AppAttestAssertionObject = parse_app_attest_assertion(parsed["assertion"])
    except Exception as e:
        raise IosPhaseFail("ASSERTION_DECODE_ERROR", f"Invalid assertion: {e}") from e

    return {"parsedToken": parsed_token, "parsedAssertion": parsed_assertion}


def verify_ios_x5c_chain(cred_cert_der: bytes, intermediate_der: bytes) -> None:
    chain_der = [cred_cert_der, intermediate_der]
    if not matches_pinned_ca(intermediate_der, APPLE_APP_ATTEST_ROOT_CAS):
        chain_der.append(pem_to_der(APPLE_APP_ATTEST_ROOT_CAS[0]))
    if not validate_certificate_path(chain_der, APPLE_APP_ATTEST_ROOT_CAS):
        raise IosPhaseFail("CHAIN_PATH_INVALID", "Certificate path validation failed")


def validate_ios_chain_validity_dates(cred_cert_der: bytes, intermediate_der: bytes, base_props: dict) -> None:
    from datetime import datetime, timezone

    now = datetime.now(timezone.utc)
    try:
        for label, der in (("credCert", cred_cert_der), ("intermediate", intermediate_der)):
            c = x509.load_der_x509_certificate(der)
            if now < c.not_valid_before_utc or now > c.not_valid_after_utc:
                raise IosPhaseFail(
                    "CHAIN_CERT_NOT_VALID",
                    f"{label} is not valid (valid from {c.not_valid_before_utc.isoformat()} to {c.not_valid_after_utc.isoformat()})",
                )
    except IosPhaseFail:
        raise
    except Exception as e:
        raise IosPhaseFail("CERT_VALIDITY_ERROR", "Failed to validate certificate validity dates") from e


def parse_attest_auth_data(auth_data: bytes) -> dict:
    if len(auth_data) < AUTH_DATA_HEADER_BYTES:
        raise IosPhaseFail("AUTHDATA_TOO_SHORT", f"authData is {len(auth_data)} bytes, < {AUTH_DATA_HEADER_BYTES}")
    header = parse_assertion_auth_data(auth_data)
    if len(auth_data) < CREDENTIAL_ID_OFFSET:
        raise IosPhaseFail("AUTHDATA_MISSING_ATTESTED", "authData missing attested credential data")
    aaguid = auth_data[AAGUID_OFFSET:CREDENTIAL_ID_LENGTH_OFFSET]
    cred_id_len = int.from_bytes(auth_data[CREDENTIAL_ID_LENGTH_OFFSET:CREDENTIAL_ID_OFFSET], "big")
    if len(auth_data) < CREDENTIAL_ID_OFFSET + cred_id_len:
        raise IosPhaseFail("AUTHDATA_TRUNCATED", "authData truncated within credentialId")
    credential_id = auth_data[CREDENTIAL_ID_OFFSET : CREDENTIAL_ID_OFFSET + cred_id_len]
    return {
        **header,
        "aaguid": aaguid,
        "credIdLen": cred_id_len,
        "credentialId": credential_id,
    }


def verify_app_id_rp_id_hash(rp_id_hash: bytes) -> dict:
    from ..config import get_attestation_config

    expected_app_id = get_attestation_config().ios_app_id
    if not expected_app_id:
        raise IosPhaseFail(
            "MISSING_APP_ID_ENV",
            'IOS_APP_ID is not configured (expected "<TeamID>.<BundleID>")',
        )
    expected_hash = hashlib.sha256(expected_app_id.encode("utf-8")).digest()
    if expected_hash != rp_id_hash:
        raise IosPhaseFail(
            "APP_ID_MISMATCH",
            "authData.rpIdHash does not match SHA-256(IOS_APP_ID)",
            extra_props={
                "rpIdHashPrefix": rp_id_hash.hex()[:12],
                "expectedAppId": expected_app_id,
            },
        )
    return {
        "expectedAppId": expected_app_id,
        "expectedRpIdHash": expected_hash,
    }


def verify_aaguid_policy(aaguid: bytes, debug_mode: bool, warnings_sink: list[str]) -> None:
    is_prod = aaguid == AAGUID_PROD
    is_dev = aaguid == AAGUID_DEV
    if not is_prod and not is_dev:
        raise IosPhaseFail(
            "UNKNOWN_AAGUID",
            f"Unknown aaguid in authData: {aaguid.hex()}",
            extra_props={"aaguidHex": aaguid.hex()},
        )
    if is_dev and not debug_mode:
        raise IosPhaseFail(
            "DEV_AAGUID_NOT_ALLOWED",
            'authData.aaguid is "appattestdevelop" but DEBUG_MODE is not enabled',
            extra_props={"aaguidUtf8": aaguid.decode("utf-8", errors="replace")},
        )
    if is_dev:
        warnings_sink.append('aaguid is "appattestdevelop" (debug mode)')


def verify_credential_id_binds_pub_key(cred_cert_der: bytes, credential_id: bytes, base_props: dict) -> dict:
    try:
        cred_cert = x509.load_der_x509_certificate(cred_cert_der)
        pubkey = cred_cert.public_key()
        point = pubkey.public_bytes(
            encoding=serialization.Encoding.X962,
            format=serialization.PublicFormat.UncompressedPoint,
        )
        if len(point) != 65 or point[0] != 0x04:
            raise IosPhaseFail("CRED_PUBKEY_FORMAT", "credCert public key is not uncompressed P-256")
    except IosPhaseFail:
        raise
    except Exception as e:
        raise IosPhaseFail("CRED_PUBKEY_EXTRACT", "Failed to extract credCert public key") from e

    expected = hashlib.sha256(point).digest()
    if expected != credential_id:
        raise IosPhaseFail(
            "CREDENTIAL_ID_MISMATCH",
            "credentialId in authData does not match SHA-256 of credCert public key",
        )
    return {"credCertPubKey": pubkey, "credCertPubKeyPoint": point}


def verify_attestation_nonce_binding(cred_cert_der: bytes, auth_data: bytes, message_data: dict) -> dict:
    import re

    if not message_data.get("publicCert"):
        raise IosPhaseFail("MISSING_PUBLIC_CERT", "Missing publicCert in message data")
    challenge_hash = message_data.get("challengeHash")
    if not challenge_hash:
        raise IosPhaseFail("MISSING_CHALLENGE_HASH", "Missing challengeHash in message data")
    if not re.fullmatch(r"[0-9a-f]{64}", challenge_hash, flags=re.IGNORECASE):
        raise IosPhaseFail("CHALLENGE_HASH_FORMAT", "challengeHash must be a 64-char hex string")
    challenge_bytes = bytes.fromhex(challenge_hash)
    expected_nonce = hashlib.sha256(auth_data + challenge_bytes).digest()

    cert_nonce = extract_nonce_from_cred_cert(cred_cert_der)
    if cert_nonce is None:
        raise IosPhaseFail(
            "CRED_NONCE_EXT_MISSING",
            "credCert is missing the App Attest nonce extension (OID 1.2.840.113635.100.8.2)",
        )
    if expected_nonce != cert_nonce:
        raise IosPhaseFail(
            "CHALLENGE_NONCE_MISMATCH",
            "App Attest challenge binding failed: cert nonce does not match SHA-256(authData || challengeHash bytes)",
            extra_props={
                "expectedPrefix": expected_nonce.hex()[:12],
                "gotPrefix": cert_nonce.hex()[:12],
            },
        )
    return {"certNonce": cert_nonce, "challengeBytes": challenge_bytes}


def verify_assertion_against_auth_cert(
    *,
    parsed_assertion: AppAttestAssertionObject,
    cred_cert_pub_key,
    attest_rp_id_hash: bytes,
    attest_sign_count: int,
    auth_cert_pem: str,
    base_props: dict,
) -> dict:
    auth_cert_der = pem_to_der(auth_cert_pem)
    auth_cert_thumbprint = hashlib.sha256(auth_cert_der).digest()
    auth_cert_thumbprint_hex = auth_cert_thumbprint.hex()

    auth_data = parsed_assertion.authenticatorData
    info = parse_assertion_auth_data(auth_data)
    assertion_rp_id_hash = info["rpIdHash"]
    assertion_flags = info["flags"]
    assertion_sign_count = info["signCount"]

    if assertion_rp_id_hash != attest_rp_id_hash:
        raise IosPhaseFail(
            "ASSERTION_RPID_MISMATCH",
            "Assertion rpIdHash does not match attestation rpIdHash",
            extra_props={
                "attestRpIdPrefix": attest_rp_id_hash.hex()[:12],
                "assertRpIdPrefix": assertion_rp_id_hash.hex()[:12],
            },
        )
    if assertion_sign_count <= attest_sign_count:
        raise IosPhaseFail(
            "ASSERTION_SIGNCOUNT_NOT_INCREMENTED",
            f"Assertion signCount ({assertion_sign_count}) is not greater than attestation signCount ({attest_sign_count})",
            extra_props={"attestSignCount": attest_sign_count, "assertSignCount": assertion_sign_count},
        )

    assertion_nonce = hashlib.sha256(auth_data + auth_cert_thumbprint).digest()
    sig_ok = verify_signature_ec_bytes(
        assertion_nonce, parsed_assertion.signature, cred_cert_pub_key, encoding="der"
    )
    signature_encoding = "der"
    if not sig_ok:
        sig_ok = verify_signature_ec_bytes(
            assertion_nonce, parsed_assertion.signature, cred_cert_pub_key, encoding="ieee-p1363"
        )
        signature_encoding = "ieee-p1363"
    if not sig_ok:
        raise IosPhaseFail(
            "ASSERTION_SIGNATURE_INVALID",
            "Assertion signature does not verify against credCert public key for nonce = SHA-256(authenticatorData || sha256(authCertDER))",
            extra_props={
                "authCertThumbprintPrefix": auth_cert_thumbprint_hex[:16],
                "signatureLength": len(parsed_assertion.signature),
                "assertSignCount": assertion_sign_count,
            },
        )
    return {
        "assertionAuthData": auth_data,
        "assertionRpIdHash": assertion_rp_id_hash,
        "assertionFlags": assertion_flags,
        "assertionSignCount": assertion_sign_count,
        "signatureEncodingUsed": signature_encoding,
        "authCertThumbprint": auth_cert_thumbprint,
        "authCertThumbprintHex": auth_cert_thumbprint_hex,
    }
