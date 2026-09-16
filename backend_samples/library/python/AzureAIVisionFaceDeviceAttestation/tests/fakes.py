"""In-memory ClusterStore for tests (mirrors the sample's memory store)."""

from __future__ import annotations

from typing import Optional
from copy import deepcopy
from threading import RLock
from uuid import uuid4

from azure_ai_vision_face_deviceattestation import CertificateData, SessionRecord
from azure_ai_vision_face_deviceattestation.store import Snapshot, UpdateResult


class InMemoryStore:
    def __init__(self) -> None:
        self.sessions: dict[str, SessionRecord] = {}
        self.certs: dict[str, CertificateData] = {}
        self.versions: dict[str, str] = {}
        self.lock = RLock()

    async def get_session(self, sid: str) -> Optional[Snapshot[SessionRecord]]:
        with self.lock:
            return Snapshot(deepcopy(self.sessions[sid]), self.versions[sid]) if sid in self.sessions else None

    async def set_session(self, sid: str, record: SessionRecord) -> bool:
        with self.lock:
            if sid in self.sessions:
                return False
            self.sessions[sid] = deepcopy(record)
            self.versions[sid] = str(uuid4())
            return True

    async def update_session(self, sid: str, expected_version: str, record: SessionRecord) -> UpdateResult:
        with self.lock:
            if sid not in self.sessions:
                return UpdateResult.MISSING_OR_EXPIRED
            if self.versions[sid] != expected_version:
                return UpdateResult.CONFLICT
            self.sessions[sid] = deepcopy(record)
            self.versions[sid] = str(uuid4())
            return UpdateResult.APPLIED

    async def get_certificate(self, thumbprint: str) -> Optional[Snapshot[CertificateData]]:
        with self.lock:
            return Snapshot(deepcopy(self.certs[thumbprint]), self.versions[thumbprint]) if thumbprint in self.certs else None

    async def set_certificate(self, thumbprint: str, data: CertificateData) -> bool:
        with self.lock:
            if thumbprint in self.certs:
                return False
            self.certs[thumbprint] = deepcopy(data)
            self.versions[thumbprint] = str(uuid4())
            return True

    async def update_certificate(self, thumbprint: str, expected_version: str, data: CertificateData) -> UpdateResult:
        with self.lock:
            if thumbprint not in self.certs:
                return UpdateResult.MISSING_OR_EXPIRED
            if self.versions[thumbprint] != expected_version:
                return UpdateResult.CONFLICT
            self.certs[thumbprint] = deepcopy(data)
            self.versions[thumbprint] = str(uuid4())
            return UpdateResult.APPLIED
