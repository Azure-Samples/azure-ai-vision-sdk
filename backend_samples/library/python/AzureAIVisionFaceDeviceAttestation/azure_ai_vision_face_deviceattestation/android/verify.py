"""
Top-level Android attestation orchestrator.
"""

from __future__ import annotations

import logging
import time
from datetime import datetime, timezone

from ..config import get_attestation_config
from ..logging import track_event, track_exception
from .chain_checks import (
    build_android_chain_der_buffers,
    parse_android_attest_json,
    validate_android_chain_validity_dates,
    verify_android_chain_revocations,
    verify_android_chain_signatures_and_root,
    verify_attestation_security_level,
    verify_chain_binds_session_challenge,
    verify_leaf_bound_to_chain0,
)
from .integrity_checks import (
    evaluate_play_integrity_verdict,
    verify_integrity_request_hash,
    verify_integrity_timestamp,
)
from .play_integrity_api import decrypt_and_verify_integrity_verdict
from .types import AndroidPhaseFail

log = logging.getLogger("android.verify")


async def verify_android_auth(message_data: dict, attest_json: str) -> dict:
    start = time.time() * 1000
    base_props = {
        "platform": "android",
        "clientId": message_data.get("clientId"),
        "challengeHashPrefix": (message_data.get("challengeHash") or "")[:8],
        "attestJsonLength": len(attest_json),
    }
    track_event("AndroidAuth.VerifyStart", base_props)

    def fail_verify(reason: str, message: str, extras=None, extra_props=None) -> dict:
        duration = time.time() * 1000 - start
        log.error("[AndroidAuth] VerifyFail %s %s", reason, message)
        track_event(
            "AndroidAuth.VerifyFail",
            {**base_props, "reason": reason, "message": message, **(extra_props or {})},
            {"durationMs": duration},
        )
        result = {
            "verified": False,
            "platform": "android",
            "message": message,
            "timestamp": datetime.now(timezone.utc).isoformat(),
        }
        if extras:
            result.update(extras)
        return result

    try:
        debug_mode = get_attestation_config().debug_mode

        try:
            attest_data = parse_android_attest_json(attest_json, base_props)
            built = build_android_chain_der_buffers(attest_data, message_data.get("publicCert"))
            cert_chain_der = built["certChainDer"]
            leaf_cert_der = built["leafCertDer"]

            verify_leaf_bound_to_chain0(leaf_cert_der, cert_chain_der)
            validity = validate_android_chain_validity_dates(leaf_cert_der, cert_chain_der, base_props)
            leaf_warning = validity.get("leafCertValidityWarning")

            chain_sig = verify_android_chain_signatures_and_root(cert_chain_der)
            root_ca_subject = chain_sig["rootCASubject"]

            verify_attestation_security_level(cert_chain_der[0])

            verify_android_chain_revocations(leaf_cert_der, cert_chain_der)

            chain_challenge_res = verify_chain_binds_session_challenge(
                cert_chain_der[0], message_data.get("challengeHash")
            )
            chain_challenge_hex = chain_challenge_res["chainChallengeHex"]

            token = attest_data.get("token")
            if not token:
                return fail_verify(
                    "MISSING_INTEGRITY_TOKEN",
                    "Play Integrity token is required but not provided",
                )

            # 8. Play Integrity verdict. Required, unless the API was unavailable /
            #    quota-exceeded and the operator opted into
            #    allow_android_attestation_when_google_unavailable — in which case the
            #    hardware Key Attestation above (incl. the session-bound keymaster
            #    challenge) stands on its own and the verdict is skipped.
            integrity_result = decrypt_and_verify_integrity_verdict(token)

            warnings: list[str] = []
            integrity_verdict = None

            if integrity_result.ok:
                integrity_verdict = integrity_result.verdict
            elif (
                integrity_result.tolerable
                and get_attestation_config().allow_android_attestation_when_google_unavailable
            ):
                warning = (
                    f"Play Integrity verdict unavailable ({integrity_result.reason}); "
                    "accepted on Key Attestation alone via allow_android_attestation_when_google_unavailable"
                )
                log.warning("[AndroidAuth] %s", warning)
                warnings.append(warning)
                track_event(
                    "AndroidAuth.IntegrityUnavailableAccepted",
                    {**base_props, "reason": integrity_result.reason},
                )
            else:
                return fail_verify(
                    "INTEGRITY_DECRYPT_FAIL",
                    "Play Integrity verification failed: could not decrypt or verify integrity token",
                )

            verdict_res = None
            if integrity_verdict:
                integrity_verdict["attestationChallenge"] = chain_challenge_hex

                verify_integrity_request_hash(integrity_verdict, leaf_cert_der)
                verify_integrity_timestamp(integrity_verdict)

                verdict_res = evaluate_play_integrity_verdict(
                    integrity_verdict, base_props, debug_mode, warnings
                )

            duration = time.time() * 1000 - start
            track_event(
                "AndroidAuth.VerifySuccess",
                {
                    **base_props,
                    "chainLength": len(cert_chain_der) + 1,
                    "rootCA": root_ca_subject,
                    "integritySkipped": integrity_verdict is None,
                    "appRecognition": verdict_res["appRecognition"] if verdict_res else None,
                    "deviceVerdicts": (",".join(verdict_res["deviceVerdicts"]) or "(empty)") if verdict_res else "(empty)",
                    "playProtect": verdict_res["playProtect"] if verdict_res else None,
                    "hasStrongIntegrity": verdict_res["hasStrongIntegrity"] if verdict_res else False,
                    "hasDeviceIntegrityFallback": verdict_res["hasDeviceIntegrity"] if verdict_res else False,
                    "hasBasicIntegrityFallback": verdict_res["hasBasicIntegrity"] if verdict_res else False,
                    "warningCount": len(warnings),
                    "leafCertValidityWarning": leaf_warning,
                    "chainChallengePrefix": chain_challenge_hex[:12],
                },
                {"durationMs": duration},
            )

            result = {
                "verified": True,
                "platform": "android",
                "message": (
                    "Android Key Attestation and Play Integrity verified successfully"
                    if integrity_verdict
                    else "Android Key Attestation verified successfully (Play Integrity unavailable, accepted by policy)"
                ),
                "timestamp": datetime.now(timezone.utc).isoformat(),
                "challengeHash": message_data.get("challengeHash"),
                "clientId": message_data.get("clientId"),
                "attestJsonLength": len(attest_json),
                "chainLength": len(cert_chain_der) + 1,
                "rootCA": root_ca_subject,
                "integrityVerdict": integrity_verdict,
            }
            if leaf_warning:
                result["leafCertValidityWarning"] = leaf_warning
            if warnings:
                result["warnings"] = warnings
            return result
        except AndroidPhaseFail as f:
            return fail_verify(f.reason, f.message, f.extras, f.extra_props)
    except Exception as err:
        log.exception("Error verifying Android attestation")
        track_exception(err, {"source": "verify_android_auth.unexpected", **base_props})
        return fail_verify(
            "UNEXPECTED_ERROR",
            f"Android attestation verification error: {err}",
        )
