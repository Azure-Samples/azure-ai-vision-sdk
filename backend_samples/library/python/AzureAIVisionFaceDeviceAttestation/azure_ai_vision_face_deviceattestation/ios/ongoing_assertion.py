"""
Per-call App Attest assertion verifier (used after registration on every
authenticated route call).
"""

from __future__ import annotations

import hashlib
import logging
from typing import Optional

from cryptography import x509

from ..config import get_attestation_config
from ..crypto_utils import verify_signature_ec_bytes
from .parsers import parse_app_attest_assertion, parse_assertion_auth_data
from .types import OngoingAssertionResult

log = logging.getLogger("ios.ongoing_assertion")

_cached_rp_id_hash: tuple[str, bytes] | None = None


def get_expected_ios_rp_id_hash() -> Optional[bytes]:
    """SHA-256(IOS_APP_ID), cached per process. None if IOS_APP_ID is unset."""
    global _cached_rp_id_hash
    app_id = get_attestation_config().ios_app_id
    if not app_id:
        return None
    if _cached_rp_id_hash and _cached_rp_id_hash[0] == app_id:
        return _cached_rp_id_hash[1]
    h = hashlib.sha256(app_id.encode("utf-8")).digest()
    _cached_rp_id_hash = (app_id, h)
    return h


def verify_ios_ongoing_assertion(
    cred_cert_pem: str,
    blob: bytes,
    assertion_b64: str,
    expected_rp_id_hash: bytes,
    last_sign_count: int,
) -> OngoingAssertionResult:
    try:
        cred_cert_pub_key = x509.load_pem_x509_certificate(cred_cert_pem.encode("ascii")).public_key()
    except Exception as e:
        return OngoingAssertionResult(
            ok=False, reason="CRED_CERT_PARSE_FAIL",
            message=f"Failed to parse persisted credCert PEM: {e}",
        )

    try:
        parsed = parse_app_attest_assertion(assertion_b64)
    except Exception as e:
        return OngoingAssertionResult(
            ok=False, reason="ASSERTION_DECODE_ERROR",
            message=f"Failed to decode App Attest assertion: {e}",
        )

    auth_data = parsed.authenticatorData
    header = parse_assertion_auth_data(auth_data)
    rp_id_hash = header["rpIdHash"]
    sign_count = header["signCount"]

    if rp_id_hash != expected_rp_id_hash:
        return OngoingAssertionResult(
            ok=False, reason="ASSERTION_RPID_MISMATCH",
            message="Assertion rpIdHash does not match SHA-256(IOS_APP_ID)",
            signCount=sign_count,
        )
    if sign_count <= last_sign_count:
        return OngoingAssertionResult(
            ok=False, reason="ASSERTION_SIGNCOUNT_NOT_INCREMENTED",
            message=f"Assertion signCount ({sign_count}) is not greater than lastSignCount ({last_sign_count})",
            signCount=sign_count,
        )

    client_data_hash = hashlib.sha256(blob).digest()
    nonce = hashlib.sha256(auth_data + client_data_hash).digest()

    sig_ok = verify_signature_ec_bytes(nonce, parsed.signature, cred_cert_pub_key, encoding="der")
    encoding = "der"
    if not sig_ok:
        sig_ok = verify_signature_ec_bytes(nonce, parsed.signature, cred_cert_pub_key, encoding="ieee-p1363")
        encoding = "ieee-p1363"
    if not sig_ok:
        return OngoingAssertionResult(
            ok=False, reason="ASSERTION_SIGNATURE_INVALID",
            message="Assertion signature does not verify against credCert public key",
            signCount=sign_count,
        )

    return OngoingAssertionResult(ok=True, signCount=sign_count, signatureEncoding=encoding)
