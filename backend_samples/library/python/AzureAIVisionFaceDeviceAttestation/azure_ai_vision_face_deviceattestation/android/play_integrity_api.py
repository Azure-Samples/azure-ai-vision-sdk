"""
Call Google's Play Integrity API to decode a client-supplied integrity token.

Reads service-account JSON from `GOOGLE_SERVICE_ACCOUNT_JSON` and the bundle id
from `ANDROID_PACKAGE_NAME`. Returns an :class:`IntegrityVerdictResult`.
"""

from __future__ import annotations

import json
import logging
import time
from dataclasses import dataclass
from typing import Optional

from ..config import get_attestation_config
from ..logging import track_dependency, track_event, track_exception

log = logging.getLogger("android.play_integrity_api")


@dataclass(frozen=True)
class IntegrityVerdictResult:
    """Outcome of a Play Integrity decode.

    ``tolerable`` is True only for the Google-unavailable (network / HTTP 5xx) or
    quota-exceeded (HTTP 429) conditions the
    ``allow_android_attestation_when_google_unavailable`` policy may accept; every
    other failure fails closed.
    """

    ok: bool
    verdict: Optional[dict] = None
    tolerable: bool = False
    reason: str = ""

    @staticmethod
    def success(verdict: dict) -> "IntegrityVerdictResult":
        return IntegrityVerdictResult(ok=True, verdict=verdict, reason="ok")

    @staticmethod
    def fail_tolerable(reason: str) -> "IntegrityVerdictResult":
        return IntegrityVerdictResult(ok=False, tolerable=True, reason=reason)

    @staticmethod
    def fail_hard(reason: str) -> "IntegrityVerdictResult":
        return IntegrityVerdictResult(ok=False, tolerable=False, reason=reason)


def _http_status(err: Exception) -> Optional[int]:
    """HTTP status code from a googleapiclient HttpError, else None."""
    try:
        from googleapiclient.errors import HttpError

        if isinstance(err, HttpError):
            status = getattr(getattr(err, "resp", None), "status", None)
            if status is not None:
                return int(status)
            status_code = getattr(err, "status_code", None)
            return int(status_code) if status_code is not None else None
    except Exception:
        pass
    return None


def _is_network_error(err: Exception) -> bool:
    """True for connectivity / timeout failures reaching Google."""
    # ConnectionError, TimeoutError, socket.timeout, socket.gaierror are OSError subclasses.
    if isinstance(err, OSError):
        return True
    for mod_name, cls_name in (("google.auth.exceptions", "TransportError"), ("httplib2", "HttpLib2Error")):
        try:
            mod = __import__(mod_name, fromlist=[cls_name])
            if isinstance(err, getattr(mod, cls_name)):
                return True
        except Exception:
            pass
    return False


def _classify_unavailable(err: Exception) -> Optional[str]:
    """Classify a decode error as a tolerable Google-unavailability condition
    ("server_unavailable" for a network error / HTTP 5xx, "quota_exceeded" for
    HTTP 429) or None when it must fail closed.
    """
    status = _http_status(err)
    if status == 429:
        return "quota_exceeded"
    if status is not None and 500 <= status <= 599:
        return "server_unavailable"
    if status is None and _is_network_error(err):
        return "server_unavailable"
    return None


def decrypt_and_verify_integrity_verdict(integrity_token: str) -> IntegrityVerdictResult:
    service_account_json = get_attestation_config().google_service_account_json
    if not service_account_json:
        log.error("GOOGLE_SERVICE_ACCOUNT_JSON not set")
        track_event(
            "AndroidAuth.PlayIntegrity.ConfigMissing", {"missing": "GOOGLE_SERVICE_ACCOUNT_JSON"}
        )
        return IntegrityVerdictResult.fail_hard("service_account_not_configured")
    try:
        credentials_info = json.loads(service_account_json)
    except Exception as err:
        track_exception(err, {"source": "decrypt_and_verify_integrity_verdict.parseCreds"})
        return IntegrityVerdictResult.fail_hard("service_account_parse_error")

    package_name = get_attestation_config().android_package_name
    if not package_name:
        track_event("AndroidAuth.PlayIntegrity.ConfigMissing", {"missing": "ANDROID_PACKAGE_NAME"})
        return IntegrityVerdictResult.fail_hard("package_name_not_configured")

    dep_start = time.time() * 1000
    try:
        from google.oauth2 import service_account
        from googleapiclient.discovery import build

        creds = service_account.Credentials.from_service_account_info(
            credentials_info,
            scopes=["https://www.googleapis.com/auth/playintegrity"],
        )
        service = build("playintegrity", "v1", credentials=creds, cache_discovery=False)

        log.info("Decrypting Play Integrity token...")
        response = (
            service.v1()
            .decodeIntegrityToken(packageName=package_name, body={"integrityToken": integrity_token})
            .execute()
        )
        dep_duration = time.time() * 1000 - dep_start

        verdict = response.get("tokenPayloadExternal")
        if not verdict:
            track_dependency(
                name="PlayIntegrity.decodeIntegrityToken",
                target="playintegrity.googleapis.com",
                data=f"packageName={package_name}",
                duration=dep_duration,
                success=False,
                result_code="empty-response",
                properties={"packageName": package_name},
            )
            return IntegrityVerdictResult.fail_hard("empty_response")

        track_dependency(
            name="PlayIntegrity.decodeIntegrityToken",
            target="playintegrity.googleapis.com",
            data=f"packageName={package_name}",
            duration=dep_duration,
            success=True,
            result_code=200,
            properties={
                "packageName": package_name,
                "appRecognition": (verdict.get("appIntegrity") or {}).get("appRecognitionVerdict"),
                "deviceRecognition": ",".join(
                    (verdict.get("deviceIntegrity") or {}).get("deviceRecognitionVerdict") or []
                ),
                "playProtect": (verdict.get("environmentDetails") or {}).get("playProtectVerdict"),
            },
        )
        return IntegrityVerdictResult.success(verdict)
    except Exception as err:
        dep_duration = time.time() * 1000 - dep_start
        log.error("Play Integrity API error: %s", err)
        track_dependency(
            name="PlayIntegrity.decodeIntegrityToken",
            target="playintegrity.googleapis.com",
            data=f"packageName={package_name}",
            duration=dep_duration,
            success=False,
            result_code="exception",
            properties={"packageName": package_name, "message": str(err)},
        )
        track_exception(err, {"source": "decrypt_and_verify_integrity_verdict", "packageName": package_name})
        kind = _classify_unavailable(err)
        return IntegrityVerdictResult.fail_tolerable(kind) if kind else IntegrityVerdictResult.fail_hard("api_error")
