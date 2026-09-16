"""
Decoders for the two CBOR payloads Apple's App Attest APIs return, plus the
credCert nonce-extension extractor.
"""

from __future__ import annotations

from typing import Optional

import cbor2
from asn1crypto import core

from ..base64_utils import decode_base64
from ..cert_utils import get_extension_value
from .constants import (
    AUTH_DATA_HEADER_BYTES,
    FLAGS_OFFSET,
    NONCE_OID,
    RP_ID_HASH_BYTES,
    SIGN_COUNT_OFFSET,
)
from .types import AppAttestAssertionObject, AppAttestObject


class _NonceExtension(core.Sequence):
    _fields = [("nonce", core.OctetString, {"explicit": 1})]


def parse_app_attest_token(token_b64: str) -> AppAttestObject:
    buf = decode_base64(token_b64)
    top = cbor2.loads(buf)
    if not isinstance(top, dict):
        raise ValueError("top-level CBOR is not a map")

    fmt = top.get("fmt")
    att_stmt = top.get("attStmt")
    auth_data = top.get("authData")
    if not isinstance(fmt, str):
        raise ValueError("missing fmt")
    if not isinstance(att_stmt, dict):
        raise ValueError("missing attStmt")
    if not isinstance(auth_data, (bytes, bytearray)):
        raise ValueError("missing authData")

    x5c = att_stmt.get("x5c")
    if not isinstance(x5c, list) or len(x5c) < 2:
        raise ValueError("attStmt.x5c must have >= 2 certs")
    cred_cert_der = x5c[0]
    intermediate_der = x5c[1]
    if not isinstance(cred_cert_der, (bytes, bytearray)) or not isinstance(intermediate_der, (bytes, bytearray)):
        raise ValueError("attStmt.x5c entries must be byte strings")

    receipt_raw = att_stmt.get("receipt")
    receipt_length = len(receipt_raw) if isinstance(receipt_raw, (bytes, bytearray)) else 0

    return AppAttestObject(
        fmt=fmt,
        authData=bytes(auth_data),
        credCertDer=bytes(cred_cert_der),
        intermediateDer=bytes(intermediate_der),
        receiptLength=receipt_length,
    )


def parse_app_attest_assertion(assertion_b64: str) -> AppAttestAssertionObject:
    buf = decode_base64(assertion_b64)
    top = cbor2.loads(buf)
    if not isinstance(top, dict):
        raise ValueError("assertion top-level CBOR is not a map")

    signature = top.get("signature")
    authenticator_data = top.get("authenticatorData")
    if not isinstance(signature, (bytes, bytearray)):
        raise ValueError("assertion missing signature")
    if not isinstance(authenticator_data, (bytes, bytearray)):
        raise ValueError("assertion missing authenticatorData")
    if len(authenticator_data) < AUTH_DATA_HEADER_BYTES:
        raise ValueError(f"assertion authenticatorData is {len(authenticator_data)} bytes, < {AUTH_DATA_HEADER_BYTES}")

    return AppAttestAssertionObject(
        signature=bytes(signature),
        authenticatorData=bytes(authenticator_data),
    )


def parse_assertion_auth_data(authenticator_data: bytes) -> dict:
    if len(authenticator_data) < AUTH_DATA_HEADER_BYTES:
        raise ValueError(f"authenticatorData is {len(authenticator_data)} bytes, < {AUTH_DATA_HEADER_BYTES}")
    rp_id_hash = authenticator_data[:RP_ID_HASH_BYTES]
    flags = authenticator_data[FLAGS_OFFSET]
    sign_count = int.from_bytes(authenticator_data[SIGN_COUNT_OFFSET:AUTH_DATA_HEADER_BYTES], "big")
    return {"rpIdHash": rp_id_hash, "flags": flags, "signCount": sign_count}


def extract_nonce_from_cred_cert(cred_cert_der: bytes) -> Optional[bytes]:
    """Pull the OCTET STRING value inside the App Attest
    nonce extension (OID 1.2.840.113635.100.8.2).
    """
    container = get_extension_value(cred_cert_der, NONCE_OID)
    if container is None:
        return None
    try:
        fields = _NonceExtension.load(container, strict=True)
        if len(fields) != 1:
            return None
        nonce = fields["nonce"].native
        return nonce if fields.dump(force=True) == container else None
    except (ValueError, TypeError):
        return None
