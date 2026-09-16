"""
Semantic checks on the decoded Play Integrity verdict.
"""

from __future__ import annotations

import hashlib
import logging
import time

from ..config import get_attestation_config
from ..logging import track_event
from .types import AndroidPhaseFail

log = logging.getLogger("android.integrity_checks")


def verify_integrity_request_hash(integrity_verdict: dict, leaf_cert_der: bytes) -> None:
    expected = hashlib.sha256(leaf_cert_der).hexdigest()
    got = (integrity_verdict.get("requestDetails") or {}).get("requestHash")
    if got != expected:
        raise AndroidPhaseFail(
            "INTEGRITY_REQUEST_HASH_MISMATCH",
            "Play Integrity verification failed: requestHash mismatch (possible replay attack)",
            extras={"integrityVerdict": integrity_verdict},
            extra_props={
                "expectedPrefix": expected[:8],
                "gotPrefix": (got or "")[:8],
            },
        )


def verify_integrity_timestamp(integrity_verdict: dict) -> None:
    ts = (integrity_verdict.get("requestDetails") or {}).get("timestampMillis")
    if ts is None or ts == "":
        raise AndroidPhaseFail(
            "INTEGRITY_TIMESTAMP_INVALID",
            "Play Integrity verification failed: token timestamp missing or invalid",
            extras={"integrityVerdict": integrity_verdict},
        )
    try:
        token_ts = int(ts)
    except (TypeError, ValueError, OverflowError):
        raise AndroidPhaseFail(
            "INTEGRITY_TIMESTAMP_INVALID",
            "Play Integrity verification failed: token timestamp missing or invalid",
            extras={"integrityVerdict": integrity_verdict},
        ) from None
    now = int(time.time() * 1000)
    five_min = 5 * 60 * 1000
    if abs(now - token_ts) > five_min:
        raise AndroidPhaseFail(
            "INTEGRITY_TIMESTAMP_OLD",
            "Play Integrity verification failed: token timestamp too old",
            extras={"integrityVerdict": integrity_verdict},
            extra_props={"tokenTimestampMs": token_ts, "skewMs": abs(now - token_ts)},
        )


def evaluate_play_integrity_verdict(
    integrity_verdict: dict,
    base_props: dict,
    debug_mode: bool,
    warnings_sink: list[str],
) -> dict:
    app_integrity = integrity_verdict.get("appIntegrity") or {}
    device_integrity = integrity_verdict.get("deviceIntegrity") or {}
    environment_details = integrity_verdict.get("environmentDetails") or {}

    expected_pkg = get_attestation_config().android_package_name
    if not expected_pkg:
        raise AndroidPhaseFail(
            "MISSING_PACKAGE_NAME_ENV",
            "ANDROID_PACKAGE_NAME is not configured",
        )

    if app_integrity.get("packageName") != expected_pkg:
        raise AndroidPhaseFail(
            "PACKAGE_NAME_MISMATCH",
            "Play Integrity verification failed: package name mismatch",
            extras={"integrityVerdict": integrity_verdict},
            extra_props={
                "expectedPackageName": expected_pkg,
                "actualPackageName": app_integrity.get("packageName"),
            },
        )

    app_recognition = app_integrity.get("appRecognitionVerdict")
    if app_recognition != "PLAY_RECOGNIZED":
        if debug_mode and app_recognition == "UNRECOGNIZED_VERSION":
            warning = f"App recognition warning (debug mode): {app_recognition}"
            log.warning(warning)
            warnings_sink.append(warning)
            track_event(
                "AndroidAuth.AppRecognitionWarning",
                {**base_props, "appRecognitionVerdict": app_recognition},
            )
        else:
            raise AndroidPhaseFail(
                "APP_NOT_RECOGNIZED",
                f"Play Integrity verification failed: app not recognized ({app_recognition})",
                extras={"integrityVerdict": integrity_verdict},
                extra_props={"appRecognitionVerdict": app_recognition},
            )

    device_verdicts = device_integrity.get("deviceRecognitionVerdict") or []
    has_strong = "MEETS_STRONG_INTEGRITY" in device_verdicts
    allow_device = get_attestation_config().allow_device_integrity or debug_mode
    has_device = allow_device and "MEETS_DEVICE_INTEGRITY" in device_verdicts
    allow_basic = get_attestation_config().allow_basic_integrity or debug_mode
    has_basic = allow_basic and "MEETS_BASIC_INTEGRITY" in device_verdicts

    if not has_strong and not has_device and not has_basic:
        raise AndroidPhaseFail(
            "DEVICE_INTEGRITY_FAIL",
            "Play Integrity verification failed: device does not meet integrity requirements",
            extras={"integrityVerdict": integrity_verdict},
            extra_props={
                "deviceVerdicts": ",".join(device_verdicts) or "(empty)",
                "allowDeviceIntegrity": allow_device,
                "allowBasicIntegrity": allow_basic,
            },
        )

    if not has_strong and has_device:
        w = f"Device integrity warning{' (debug mode)' if debug_mode else ''}: MEETS_DEVICE_INTEGRITY only, not MEETS_STRONG_INTEGRITY"
        warnings_sink.append(w)
        track_event(
            "AndroidAuth.DeviceIntegrityWarning",
            {**base_props, "deviceVerdicts": ",".join(device_verdicts) or "(empty)",
             "acceptedAs": "MEETS_DEVICE_INTEGRITY", "debugMode": debug_mode},
        )
    elif not has_strong and has_basic:
        w = f"Device integrity warning{' (debug mode)' if debug_mode else ''}: MEETS_BASIC_INTEGRITY only, not MEETS_STRONG_INTEGRITY"
        warnings_sink.append(w)
        track_event(
            "AndroidAuth.DeviceIntegrityWarning",
            {**base_props, "deviceVerdicts": ",".join(device_verdicts) or "(empty)",
             "acceptedAs": "MEETS_BASIC_INTEGRITY", "debugMode": debug_mode},
        )

    play_protect = environment_details.get("playProtectVerdict")
    if play_protect and play_protect != "NO_ISSUES":
        warnings_sink.append(f"Play Protect verdict: {play_protect}")

    apps_detected = (environment_details.get("appAccessRiskVerdict") or {}).get("appsDetected") or []
    if apps_detected:
        warnings_sink.append(f"App Access Risk - detected apps: {', '.join(apps_detected)}")

    return {
        "appRecognition": app_recognition,
        "deviceVerdicts": device_verdicts,
        "playProtect": play_protect,
        "hasStrongIntegrity": has_strong,
        "hasDeviceIntegrity": has_device,
        "hasBasicIntegrity": has_basic,
    }
