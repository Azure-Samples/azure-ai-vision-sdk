"""Verify a fresh App Attest assertion + advance the persisted signCount.

Used by every iOS-aware route (verify / token / digest) after registration.
Framework-agnostic: failures are returned as a :class:`HandlerOutcome` via
``fail``, never as an HTTP response.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Awaitable, Callable, Optional

from .api_telemetry import track_api_fail
from .cert_store import get_certificate, update_certificate_metadata
from .handlers.types import HandlerOutcome, fail
from .ios import get_expected_ios_rp_id_hash, verify_ios_ongoing_assertion
from .store import ClusterStore


@dataclass
class IosAssertionCheckResult:
    ok: bool
    result: Optional[HandlerOutcome] = None
    commit: Optional[Callable[[], Awaitable[bool]]] = None


async def check_ios_assertion_for_route(
    *,
    store: ClusterStore,
    route_name: str,
    session_id: str,
    thumbprint: str,
    blob: bytes,
    assertion: Optional[str],
) -> IosAssertionCheckResult:
    """Verify a fresh App Attest assertion bound to ``blob``. Mirrors Node behavior."""
    if not assertion or not isinstance(assertion, str):
        return IosAssertionCheckResult(
            ok=False,
            result=fail(
                route_name, 401, "MISSING_ASSERTION", "Missing iOS App Attest assertion",
                properties={"sid": session_id, "thumbprint": thumbprint},
            ),
        )

    cert_record = await get_certificate(store, thumbprint)
    if not cert_record:
        return IosAssertionCheckResult(
            ok=False,
            result=fail(
                route_name, 401, "CERT_RECORD_MISSING", "Certificate record not found",
                properties={"sid": session_id, "thumbprint": thumbprint},
            ),
        )

    cert_metadata = cert_record.value.metadata or {}
    verdict = cert_metadata.get("appAttestVerdict") or {}
    cred_cert_pem = verdict.get("credCertPem")
    if not cred_cert_pem:
        return IosAssertionCheckResult(
            ok=False,
            result=fail(
                route_name, 401, "LEGACY_CERT_NO_CREDCERT_PEM",
                "Certificate predates assertion requirement; re-registration required",
                properties={"sid": session_id, "thumbprint": thumbprint},
            ),
        )

    expected_rp_id_hash = get_expected_ios_rp_id_hash()
    if not expected_rp_id_hash:
        return IosAssertionCheckResult(
            ok=False,
            result=fail(
                route_name, 500, "MISSING_IOS_APP_ID", "Server misconfigured: IOS_APP_ID not set",
                properties={"sid": session_id},
            ),
        )

    last_sign_count_raw = cert_metadata.get("lastAssertionSignCount")
    if isinstance(last_sign_count_raw, int):
        last_sign_count = last_sign_count_raw
    else:
        last_sign_count = (verdict.get("assertion") or {}).get("signCount") or 0

    assertion_result = verify_ios_ongoing_assertion(
        cred_cert_pem, blob, assertion, expected_rp_id_hash, last_sign_count
    )

    if not assertion_result.ok:
        return IosAssertionCheckResult(
            ok=False,
            result=fail(
                route_name, 401, "ASSERTION_VERIFY_FAIL", "iOS assertion verification failed",
                properties={
                    "sid": session_id,
                    "thumbprint": thumbprint,
                    "reason": assertion_result.reason,
                    "message": assertion_result.message,
                    "signCount": assertion_result.signCount,
                    "lastSignCount": last_sign_count,
                },
            ),
        )

    async def commit() -> bool:
        return await update_certificate_metadata(
            store, thumbprint, {"lastAssertionSignCount": assertion_result.signCount}, cert_record
        )
    return IosAssertionCheckResult(ok=True, commit=commit)
