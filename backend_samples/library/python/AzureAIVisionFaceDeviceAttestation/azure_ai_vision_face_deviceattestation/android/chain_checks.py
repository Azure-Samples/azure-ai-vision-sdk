"""
Per-phase certificate-chain validators for Android Key Attestation.
"""

from __future__ import annotations

import json
import logging
import re
from datetime import datetime, timezone
from typing import Optional

from cryptography import x509

from ..logging import track_event, track_exception
from ..base64_utils import decode_base64
from ..cert_utils import (
    GOOGLE_HARDWARE_ATTESTATION_ROOT_CAS,
    matches_pinned_ca,
    pem_to_der,
    validate_certificate_path,
)
from .keymaster_ext import (
    extract_attestation_challenge_from_cert,
    is_hardware_attestation_security_level,
    parse_key_description,
)
from .revocation import check_certificate_revocation, fetch_revocation_status_list
from .types import AndroidPhaseFail

log = logging.getLogger("android.chain_checks")


def parse_android_attest_json(attest_json: str, base_props: dict) -> dict:
    try:
        data = json.loads(attest_json)
    except Exception as err:
        log.error("[AndroidAuth] Failed to parse attestJson")
        track_exception(err, {"source": "verify_android_auth.parseAttestJson", **base_props})
        raise AndroidPhaseFail("ATTEST_JSON_PARSE_ERROR", "Invalid attestation: failed to parse attestJson") from err

    cc = data.get("certificateChain")
    if cc is None or not isinstance(cc, list):
        raise AndroidPhaseFail("INVALID_CHAIN_FORMAT", "Invalid attestation: missing or invalid certificateChain")
    if len(cc) == 0:
        raise AndroidPhaseFail("EMPTY_CHAIN", "Invalid attestation: empty certificateChain")
    return data


def build_android_chain_der_buffers(attest_data: dict, public_cert_pem: Optional[str]) -> dict:
    if not public_cert_pem:
        raise AndroidPhaseFail("MISSING_PUBLIC_CERT", "Missing publicCert in message data")
    cert_chain_der = [decode_base64(c) for c in attest_data["certificateChain"]]
    leaf_cert_der = pem_to_der(public_cert_pem)
    return {"certChainDer": cert_chain_der, "leafCertDer": leaf_cert_der}


def verify_leaf_bound_to_chain0(leaf_cert_der: bytes, cert_chain_der: list[bytes]) -> None:
    if leaf_cert_der != cert_chain_der[0]:
        raise AndroidPhaseFail(
            "LEAF_CHAIN_MISMATCH",
            "Certificate chain verification failed: publicCert must match certificateChain[0]",
            extras={"chainLength": len(cert_chain_der)},
            extra_props={"chainLength": len(cert_chain_der)},
        )


def validate_android_chain_validity_dates(
    leaf_cert_der: bytes,
    cert_chain_der: list[bytes],
    base_props: dict,
) -> dict:
    try:
        leaf_cert = x509.load_der_x509_certificate(leaf_cert_der)
        now = datetime.now(timezone.utc)
        leaf_warning = None
        if now < leaf_cert.not_valid_before_utc or now > leaf_cert.not_valid_after_utc:
            leaf_warning = (
                f"Leaf certificate validity warning: valid from "
                f"{leaf_cert.not_valid_before_utc.isoformat()} to {leaf_cert.not_valid_after_utc.isoformat()}"
            )
            track_event(
                "AndroidAuth.LeafCertValidityWarning",
                {**base_props,
                 "validFrom": leaf_cert.not_valid_before_utc.isoformat(),
                 "validTo": leaf_cert.not_valid_after_utc.isoformat()},
            )

        for i, der in enumerate(cert_chain_der):
            c = x509.load_der_x509_certificate(der)
            if now < c.not_valid_before_utc or now > c.not_valid_after_utc:
                raise AndroidPhaseFail(
                    "CHAIN_CERT_NOT_VALID",
                    f"Certificate at index {i} is not valid (valid from {c.not_valid_before_utc.isoformat()} to {c.not_valid_after_utc.isoformat()})",
                    extras={"chainLength": len(cert_chain_der) + 1},
                    extra_props={
                        "chainIndex": i,
                        "certValidFrom": c.not_valid_before_utc.isoformat(),
                        "certValidTo": c.not_valid_after_utc.isoformat(),
                    },
                )
        return {"leafCertValidityWarning": leaf_warning}
    except AndroidPhaseFail:
        raise
    except Exception as err:
        track_exception(err, {"source": "verify_android_auth.validityDates", **base_props})
        raise AndroidPhaseFail("CERT_VALIDITY_ERROR", "Failed to validate certificate validity dates") from err


def verify_android_chain_signatures_and_root(cert_chain_der: list[bytes]) -> dict:
    root = cert_chain_der[-1]
    if not matches_pinned_ca(root, GOOGLE_HARDWARE_ATTESTATION_ROOT_CAS):
        raise AndroidPhaseFail(
            "ROOT_CA_MISMATCH",
            "Certificate chain verification failed: root CA does not match any pinned CA",
            extras={"chainLength": len(cert_chain_der)},
            extra_props={"chainLength": len(cert_chain_der)},
        )

    if not validate_certificate_path(cert_chain_der, GOOGLE_HARDWARE_ATTESTATION_ROOT_CAS):
        raise AndroidPhaseFail(
            "CHAIN_PATH_INVALID",
            "Certificate path validation failed",
            extras={"chainLength": len(cert_chain_der)},
            extra_props={"chainLength": len(cert_chain_der)},
        )

    root_ca_subject = "unknown"
    try:
        root_ca_subject = x509.load_der_x509_certificate(root).subject.rfc4514_string()
    except Exception as err:
        log.error("Failed to parse root CA subject: %s", err)
    return {"rootCASubject": root_ca_subject}


def verify_attestation_security_level(chain0_der: bytes) -> None:
    description = parse_key_description(chain0_der)
    if description is None:
        raise AndroidPhaseFail(
            "KEYMASTER_EXT_MISSING",
            "Android Key Attestation extension (OID 1.3.6.1.4.1.11129.2.1.17) missing or unparseable on leaf attestation cert",
        )
    if not is_hardware_attestation_security_level(description):
        raise AndroidPhaseFail(
            "ATTESTATION_SECURITY_LEVEL_INVALID",
            "Android Key Attestation verification failed: attestationSecurityLevel must be TrustedEnvironment or StrongBox",
            extra_props={"attestationSecurityLevel": description.attestationSecurityLevel},
        )


def verify_android_chain_revocations(leaf_cert_der: bytes, cert_chain_der: list[bytes]) -> None:
    status_list = fetch_revocation_status_list()

    leaf_check = check_certificate_revocation(leaf_cert_der, status_list)
    if leaf_check.get("isRevoked"):
        raise AndroidPhaseFail(
            "LEAF_REVOKED",
            f"Leaf certificate has been {leaf_check.get('status')}: {leaf_check.get('reason')}",
            extras={"chainLength": len(cert_chain_der) + 1},
            extra_props={
                "revocationStatus": leaf_check.get("status"),
                "revocationReason": leaf_check.get("reason"),
            },
        )

    for i, der in enumerate(cert_chain_der):
        rc = check_certificate_revocation(der, status_list)
        if rc.get("isRevoked"):
            raise AndroidPhaseFail(
                "CHAIN_CERT_REVOKED",
                f"Certificate at index {i} has been {rc.get('status')}: {rc.get('reason')}",
                extras={"chainLength": len(cert_chain_der) + 1},
                extra_props={
                    "chainIndex": i,
                    "revocationStatus": rc.get("status"),
                    "revocationReason": rc.get("reason"),
                },
            )


def verify_chain_binds_session_challenge(chain0_der: bytes, session_challenge_hash: Optional[str]) -> dict:
    if not session_challenge_hash:
        raise AndroidPhaseFail("MISSING_CHALLENGE_HASH", "Missing challengeHash in message data")
    if not re.fullmatch(r"[0-9a-f]+", session_challenge_hash, flags=re.IGNORECASE) or len(session_challenge_hash) % 2 != 0:
        raise AndroidPhaseFail("CHALLENGE_HASH_FORMAT", "challengeHash must be a hex string of even length")
    session_bytes = bytes.fromhex(session_challenge_hash)
    chain_challenge = extract_attestation_challenge_from_cert(chain0_der)
    if chain_challenge is None:
        raise AndroidPhaseFail(
            "KEYMASTER_EXT_MISSING",
            "Android Key Attestation extension (OID 1.3.6.1.4.1.11129.2.1.17) missing or unparseable on leaf attestation cert",
        )
    if chain_challenge != session_bytes:
        raise AndroidPhaseFail(
            "CHAIN_CHALLENGE_MISMATCH",
            "Android Key Attestation chain challenge does not match session challengeHash",
            extra_props={
                "expectedPrefix": session_bytes.hex()[:12],
                "gotPrefix": chain_challenge.hex()[:12],
            },
        )
    return {"chainChallengeHex": chain_challenge.hex()}
