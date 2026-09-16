"""Certificate records, keyed by the SHA-256 thumbprint of the cert DER.

Domain logic only: the injected :class:`ClusterStore` owns persistence, TTL, and
serialization. A record is a :class:`CertificateData`; re-verifying an existing
cert bumps ``last_verified_at`` (preserving its TTL).
"""

from __future__ import annotations

import logging
import time
from datetime import datetime, timezone
from typing import Any, Optional

from .cert_utils import compute_cert_thumbprint
from .logging import track_event, track_exception
from .store import CertificateData, ClusterStore, Snapshot, StorageError, UpdateResult

log = logging.getLogger("cert_store")


def _iso_now() -> str:
    return (
        datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.")
        + f"{int(time.time() * 1000) % 1000:03d}Z"
    )


async def save_certificate(
    store: ClusterStore,
    client_id: str,
    system: str,
    public_cert: str,
    metadata: Optional[dict[str, Any]] = None,
) -> Optional[dict]:
    """Create or bump a certificate record. Returns {thumbprint, isNew} or None."""
    thumbprint = compute_cert_thumbprint(public_cert)
    if not thumbprint:
        log.error("Failed to compute certificate thumbprint")
        track_event(
            "CertStore.SaveFail",
            {"reason": "THUMBPRINT_COMPUTE_FAIL", "clientId": client_id, "system": system},
        )
        return None
    try:
        existing = await store.get_certificate(thumbprint)
        if existing:
            if existing.value.client_id != client_id or existing.value.system != system:
                return None
            existing.value.last_verified_at = _iso_now()
            if await store.update_certificate(thumbprint, existing.version, existing.value) != UpdateResult.APPLIED:
                return None
            track_event(
                "CertStore.Updated",
                {"thumbprint": thumbprint, "clientId": client_id, "system": system, "isNew": False},
            )
            return {"thumbprint": thumbprint, "isNew": False}

        now = _iso_now()
        cert_data = CertificateData(
            client_id=client_id,
            system=system,
            thumbprint=thumbprint,
            public_cert=public_cert,
            created_at=now,
            last_verified_at=now,
            metadata=metadata,
        )
        if not await store.set_certificate(thumbprint, cert_data):
            return None
        track_event(
            "CertStore.Created",
            {"thumbprint": thumbprint, "clientId": client_id, "system": system, "isNew": True},
        )
        return {"thumbprint": thumbprint, "isNew": True}
    except Exception as err:  # noqa: BLE001
        log.error("Error saving certificate: %s", err)
        track_exception(err, {"source": "save_certificate", "thumbprint": thumbprint})
        raise StorageError("Could not save certificate") from err


async def get_certificate(store: ClusterStore, thumbprint: str) -> Optional[Snapshot[CertificateData]]:
    try:
        return await store.get_certificate(thumbprint)
    except Exception as err:  # noqa: BLE001
        log.error("Error retrieving certificate: %s", err)
        track_exception(err, {"source": "get_certificate", "thumbprint": thumbprint})
        raise StorageError("Could not read certificate") from err


async def update_certificate_metadata(
    store: ClusterStore, thumbprint: str, partial_metadata: dict[str, Any], snapshot: Snapshot[CertificateData]
) -> bool:
    """Merge fields into metadata + bump last_verified_at (store preserves TTL)."""
    try:
        existing = snapshot.value
        existing.last_verified_at = _iso_now()
        merged = dict(existing.metadata or {})
        merged.update(partial_metadata)
        existing.metadata = merged
        return await store.update_certificate(thumbprint, snapshot.version, existing) == UpdateResult.APPLIED
    except Exception as err:  # noqa: BLE001
        track_exception(err, {"source": "update_certificate_metadata", "thumbprint": thumbprint})
        raise StorageError("Could not update certificate") from err
