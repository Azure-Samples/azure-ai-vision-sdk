"""
Google Android Key Attestation certificate revocation list:
https://android.googleapis.com/attestation/status

Fetched lazily, cached in-process (Cache-Control max-age honored, default 1h),
fails closed: an unreachable list is treated as REVOKED for every cert.
"""

from __future__ import annotations

import logging
import re
import time
from dataclasses import dataclass, field
from typing import Any, Optional

import requests
from cryptography import x509

from ..logging import track_dependency, track_event, track_exception

log = logging.getLogger("android.revocation")

_REVOCATION_URL = "https://android.googleapis.com/attestation/status"
_DEFAULT_TTL_MS = 3600 * 1000  # 1 hour

_cache: dict[str, Any] = {"data": None, "fetched_at": 0.0, "ttl_ms": _DEFAULT_TTL_MS}


def fetch_revocation_status_list() -> Optional[dict]:
    now = time.time() * 1000
    if _cache["data"] is not None and (now - _cache["fetched_at"]) < _cache["ttl_ms"]:
        track_event(
            "AndroidAuth.RevocationList.CacheHit",
            {
                "ageMs": int(now - _cache["fetched_at"]),
                "ttlMs": _cache["ttl_ms"],
                "entryCount": len((_cache["data"] or {}).get("entries") or {}),
            },
        )
        return _cache["data"]

    dep_start = time.time() * 1000
    try:
        resp = requests.get(_REVOCATION_URL, timeout=10)
        dep_duration = time.time() * 1000 - dep_start
        if not resp.ok:
            log.error("Revocation fetch failed: %s %s", resp.status_code, resp.reason)
            track_dependency(
                name="AndroidAttestation.RevocationList",
                target="android.googleapis.com",
                data=_REVOCATION_URL,
                duration=dep_duration,
                success=False,
                result_code=resp.status_code,
                properties={"statusText": resp.reason},
            )
            return None

        cache_control = resp.headers.get("cache-control") or ""
        m = re.search(r"max-age=(\d+)", cache_control)
        if m:
            _cache["ttl_ms"] = int(m.group(1)) * 1000

        status_list = resp.json()
        _cache["data"] = status_list
        _cache["fetched_at"] = now
        entry_count = len((status_list or {}).get("entries") or {})
        track_dependency(
            name="AndroidAttestation.RevocationList",
            target="android.googleapis.com",
            data=_REVOCATION_URL,
            duration=dep_duration,
            success=True,
            result_code=200,
            properties={"entryCount": entry_count, "ttlMs": _cache["ttl_ms"]},
        )
        return status_list
    except Exception as err:
        dep_duration = time.time() * 1000 - dep_start
        track_dependency(
            name="AndroidAttestation.RevocationList",
            target="android.googleapis.com",
            data=_REVOCATION_URL,
            duration=dep_duration,
            success=False,
            result_code="exception",
        )
        track_exception(err, {"source": "fetch_revocation_status_list"})
        return None


def check_certificate_revocation(cert_der: bytes, status_list: Optional[dict]) -> dict:
    """Return {isRevoked, reason?, status?}."""
    if not status_list or not status_list.get("entries"):
        # Fail closed
        log.error("Revocation list unavailable — failing closed")
        return {"isRevoked": True, "reason": "REVOCATION_CHECK_UNAVAILABLE", "status": "REVOKED"}

    try:
        cert = x509.load_der_x509_certificate(cert_der)
        serial_hex = format(cert.serial_number, "x").lower()
        entry = status_list["entries"].get(serial_hex)
        if entry:
            return {
                "isRevoked": True,
                "status": entry.get("status"),
                "reason": entry.get("reason") or "UNSPECIFIED",
            }
        return {"isRevoked": False}
    except Exception as err:
        log.error("Revocation check error: %s", err)
        return {"isRevoked": False}
