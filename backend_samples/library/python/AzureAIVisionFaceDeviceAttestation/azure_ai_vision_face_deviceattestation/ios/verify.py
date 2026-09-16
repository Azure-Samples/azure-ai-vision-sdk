"""
Top-level iOS App Attest verifier — sequences the attestation phases.
"""

from __future__ import annotations

import logging
import time
from dataclasses import asdict
from datetime import datetime, timezone

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization

from ..config import get_attestation_config
from ..logging import track_event, track_exception
from ..cert_utils import pem_to_der
from .attestation_phases import (
    parse_attest_auth_data,
    parse_ios_attest_envelope,
    validate_ios_chain_validity_dates,
    verify_aaguid_policy,
    verify_app_id_rp_id_hash,
    verify_assertion_against_auth_cert,
    verify_attestation_nonce_binding,
    verify_credential_id_binds_pub_key,
    verify_ios_x5c_chain,
)
from .types import (
    AppAttestVerdict,
    AssertionSummary,
    CredCertSummary,
    IntermediateCertSummary,
    IosPhaseFail,
)

log = logging.getLogger("ios.verify")


def _cert_to_dict_summary(cert: x509.Certificate) -> tuple[str, str, str, str, str]:
    return (
        cert.subject.rfc4514_string(),
        cert.issuer.rfc4514_string(),
        format(cert.serial_number, "X"),
        cert.not_valid_before_utc.isoformat(),
        cert.not_valid_after_utc.isoformat(),
    )


async def verify_ios_auth(message_data: dict, attest_json: str) -> dict:
    start = time.time() * 1000
    base_props = {
        "platform": "ios",
        "clientId": message_data.get("clientId"),
        "challengeHashPrefix": (message_data.get("challengeHash") or "")[:8],
        "attestJsonLength": len(attest_json),
    }
    track_event("IosAuth.VerifyStart", base_props)

    def fail_verify(reason: str, message: str, extras=None, extra_props=None) -> dict:
        duration = time.time() * 1000 - start
        log.error("[iOSAuth] VerifyFail %s %s", reason, message)
        track_event(
            "IosAuth.VerifyFail",
            {**base_props, "reason": reason, "message": message, **(extra_props or {})},
            {"durationMs": duration},
        )
        result = {
            "verified": False,
            "platform": "ios",
            "message": message,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        if extras:
            result.update(extras)
        return result

    try:
        debug_mode = get_attestation_config().debug_mode
        try:
            env = parse_ios_attest_envelope(attest_json, base_props)
            parsed_token = env["parsedToken"]
            parsed_assertion = env["parsedAssertion"]

            validate_ios_chain_validity_dates(
                parsed_token.credCertDer, parsed_token.intermediateDer, base_props
            )
            verify_ios_x5c_chain(parsed_token.credCertDer, parsed_token.intermediateDer)

            warnings: list[str] = []

            ad = parse_attest_auth_data(parsed_token.authData)
            rp_id_hash = ad["rpIdHash"]
            sign_count = ad["signCount"]
            aaguid = ad["aaguid"]
            credential_id = ad["credentialId"]

            app_id_res = verify_app_id_rp_id_hash(rp_id_hash)
            expected_app_id = app_id_res["expectedAppId"]

            verify_aaguid_policy(aaguid, debug_mode, warnings)

            cred_bind = verify_credential_id_binds_pub_key(parsed_token.credCertDer, credential_id, base_props)
            cred_cert_pub_key = cred_bind["credCertPubKey"]

            nonce_res = verify_attestation_nonce_binding(parsed_token.credCertDer, parsed_token.authData, message_data)
            cert_nonce = nonce_res["certNonce"]
            challenge_bytes = nonce_res["challengeBytes"]

            assertion_res = verify_assertion_against_auth_cert(
                parsed_assertion=parsed_assertion,
                cred_cert_pub_key=cred_cert_pub_key,
                attest_rp_id_hash=rp_id_hash,
                attest_sign_count=sign_count,
                auth_cert_pem=message_data["publicCert"],
                base_props=base_props,
            )

            duration = time.time() * 1000 - start
            cred_cert = x509.load_der_x509_certificate(parsed_token.credCertDer)
            int_cert = x509.load_der_x509_certificate(parsed_token.intermediateDer)
            import hashlib as _h
            cred_cert_thumbprint = _h.sha256(parsed_token.credCertDer).hexdigest()
            aaguid_utf8 = aaguid.rstrip(b"\x00").decode("utf-8", errors="replace")
            cred_cert_pem = cred_cert.public_bytes(serialization.Encoding.PEM).decode("ascii")

            cred_sub, cred_iss, cred_sn, cred_vf, cred_vt = _cert_to_dict_summary(cred_cert)
            int_sub, int_iss, int_sn, int_vf, int_vt = _cert_to_dict_summary(int_cert)

            verdict = AppAttestVerdict(
                fmt=parsed_token.fmt,
                rpIdHash=rp_id_hash.hex(),
                appId=expected_app_id,
                aaguid=aaguid_utf8,
                flags=ad["flags"],
                signCount=sign_count,
                credentialId=credential_id.hex(),
                credentialIdMatchesPubKey=True,
                credCert=CredCertSummary(cred_sub, cred_iss, cred_sn, cred_vf, cred_vt, cred_cert_thumbprint),
                credCertPem=cred_cert_pem,
                intermediateCert=IntermediateCertSummary(int_sub, int_iss, int_sn, int_vf, int_vt),
                receiptLength=parsed_token.receiptLength,
                authDataLength=len(parsed_token.authData),
                nonceExtension=cert_nonce.hex(),
                expectedClientDataHash=challenge_bytes.hex(),
                authCertThumbprint=assertion_res["authCertThumbprintHex"],
                nonceVerified=True,
                assertion=AssertionSummary(
                    authenticatorDataLength=len(assertion_res["assertionAuthData"]),
                    rpIdHash=assertion_res["assertionRpIdHash"].hex(),
                    flags=assertion_res["assertionFlags"],
                    signCount=assertion_res["assertionSignCount"],
                    signatureLength=len(parsed_assertion.signature),
                    signatureEncoding=assertion_res["signatureEncodingUsed"],
                    expectedClientDataHash=assertion_res["authCertThumbprintHex"],
                    signatureVerified=True,
                ),
                challengeBinding=(
                    "attest: sha256(authData || challengeHashBytes);  "
                    "assert: ECDSA-SHA256(credCertPubKey, nonce = sha256(authenticatorData || sha256(authCertDER)))"
                ),
            )

            track_event(
                "IosAuth.VerifySuccess",
                {
                    **base_props,
                    "rpIdHashPrefix": rp_id_hash.hex()[:12],
                    "credCertSubject": cred_cert.subject.rfc4514_string(),
                    "aaguid": aaguid_utf8,
                    "flags": ad["flags"],
                    "attestSignCount": sign_count,
                    "assertSignCount": assertion_res["assertionSignCount"],
                    "warningCount": len(warnings),
                },
                {"durationMs": duration},
            )

            result = {
                "verified": True,
                "platform": "ios",
                "message": "iOS App Attest verified successfully",
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "challengeHash": message_data.get("challengeHash"),
                "clientId": message_data.get("clientId"),
                "attestJsonLength": len(attest_json),
                "chainLength": 2,
                "rootCA": "Apple App Attestation Root CA",
                "appAttestVerdict": asdict(verdict),
            }
            if warnings:
                result["warnings"] = warnings
            return result
        except IosPhaseFail as f:
            return fail_verify(f.reason, f.message, f.extras, f.extra_props)
    except Exception as err:
        log.exception("[iOSAuth] Unexpected error")
        track_exception(err, {"source": "verify_ios_auth.unexpected", **base_props})
        return fail_verify("UNEXPECTED_ERROR", f"iOS attestation verification error: {err}")
