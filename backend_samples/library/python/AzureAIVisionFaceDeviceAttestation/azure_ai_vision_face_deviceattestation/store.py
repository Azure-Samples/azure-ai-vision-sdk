"""Persistence seam for the attestation library.

The library defines the :class:`ClusterStore` interface (session records +
certificate records); the host supplies the implementation (Redis, SQL,
in-memory, ...). The store owns all TTL policy, key namespacing, serialization,
and connection lifecycle — the library only calls these six async methods.

Mirrors the ``ClusterStore`` / ``SessionRecord`` / ``CertificateData`` shapes of
the Node.js and .NET packages.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from enum import Enum
from typing import Any, Generic, Optional, Protocol, TypeVar

RecordT = TypeVar("RecordT")


@dataclass(frozen=True)
class Snapshot(Generic[RecordT]):
    value: RecordT
    version: str


class UpdateResult(str, Enum):
    APPLIED = "applied"
    CONFLICT = "conflict"
    MISSING_OR_EXPIRED = "missingOrExpired"


class StorageError(RuntimeError):
    pass


@dataclass
class SessionRecord:
    """A stored session: the Face session token plus mutable attestation state."""

    token: str
    data: dict[str, Any] = field(default_factory=dict)


@dataclass
class CertificateData:
    """A cached client certificate, keyed by the SHA-256 thumbprint of its DER."""

    client_id: str
    system: str  # 'ios' | 'android'
    thumbprint: str
    public_cert: str
    created_at: str
    last_verified_at: str
    metadata: Optional[dict[str, Any]] = None


class ClusterStore(Protocol):
    """Detached reads, create-if-absent, and atomic version-conditional updates.

    Each write assigns a fresh unique version, including recreation after expiry.
    Updates preserve expiry and reject expired records. Failures raise StorageError.
    """

    async def get_session(self, sid: str) -> Optional[Snapshot[SessionRecord]]: ...
    async def set_session(self, sid: str, record: SessionRecord) -> bool: ...
    async def update_session(self, sid: str, expected_version: str, record: SessionRecord) -> UpdateResult: ...

    async def get_certificate(self, thumbprint: str) -> Optional[Snapshot[CertificateData]]: ...
    async def set_certificate(self, thumbprint: str, data: CertificateData) -> bool: ...
    async def update_certificate(self, thumbprint: str, expected_version: str, data: CertificateData) -> UpdateResult: ...
