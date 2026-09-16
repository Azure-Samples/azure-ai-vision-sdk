"""Process-local CAS store for development. State is lost on restart."""

from __future__ import annotations

import os
from copy import deepcopy
from threading import RLock
from time import monotonic
from typing import Optional, TypeVar
from uuid import uuid4

from azure_ai_vision_face_deviceattestation import CertificateData, SessionRecord, Snapshot, StorageError, UpdateResult

RecordT = TypeVar("RecordT", SessionRecord, CertificateData)


class MemoryStore:
    def __init__(self) -> None:
        self._sessions: dict[str, tuple[Snapshot[SessionRecord], float]] = {}
        self._certs: dict[str, tuple[Snapshot[CertificateData], float]] = {}
        self._lock = RLock()
        self._session_ttl = int(os.getenv("SESSION_TOKEN_TTL", "600"))
        self._cert_ttl = int(os.getenv("CERT_TTL", "604800"))
        if min(self._session_ttl, self._cert_ttl) <= 0:
            raise StorageError("Invalid attestation TTL")

    def _read(self, entries: dict[str, tuple[Snapshot[RecordT], float]], key: str) -> Optional[Snapshot[RecordT]]:
        with self._lock:
            entry = entries.get(key)
            if entry is None:
                return None
            if entry[1] <= monotonic():
                del entries[key]
                return None
            return deepcopy(entry[0])

    def _create(self, entries: dict[str, tuple[Snapshot[RecordT], float]], key: str, value: RecordT, ttl: int) -> bool:
        with self._lock:
            if self._read(entries, key) is not None:
                return False
            entries[key] = (Snapshot(deepcopy(value), str(uuid4())), monotonic() + ttl)
            return True

    def _update(self, entries: dict[str, tuple[Snapshot[RecordT], float]], key: str, version: str, value: RecordT) -> UpdateResult:
        with self._lock:
            current = self._read(entries, key)
            if current is None:
                return UpdateResult.MISSING_OR_EXPIRED
            if current.version != version:
                return UpdateResult.CONFLICT
            entries[key] = (Snapshot(deepcopy(value), str(uuid4())), entries[key][1])
            return UpdateResult.APPLIED

    async def get_session(self, sid: str) -> Optional[Snapshot[SessionRecord]]:
        return self._read(self._sessions, sid)

    async def set_session(self, sid: str, record: SessionRecord) -> bool:
        return self._create(self._sessions, sid, record, self._session_ttl)

    async def update_session(self, sid: str, expected_version: str, record: SessionRecord) -> UpdateResult:
        return self._update(self._sessions, sid, expected_version, record)

    async def get_certificate(self, thumbprint: str) -> Optional[Snapshot[CertificateData]]:
        return self._read(self._certs, thumbprint)

    async def set_certificate(self, thumbprint: str, data: CertificateData) -> bool:
        return self._create(self._certs, thumbprint, data, self._cert_ttl)

    async def update_certificate(self, thumbprint: str, expected_version: str, data: CertificateData) -> UpdateResult:
        return self._update(self._certs, thumbprint, expected_version, data)