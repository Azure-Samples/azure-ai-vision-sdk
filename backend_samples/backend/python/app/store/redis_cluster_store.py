"""Redis snapshots with create-if-absent and single-key atomic CAS."""

from __future__ import annotations

import json
import os
from dataclasses import asdict
from typing import Any, Callable, Optional, TypeVar
from uuid import uuid4

from azure_ai_vision_face_deviceattestation import CertificateData, SessionRecord, Snapshot, StorageError, UpdateResult

from .redis_client import now_ms, open_client, track_redis_op

RecordT = TypeVar("RecordT", SessionRecord, CertificateData)
_CAS_SCRIPT = """
local current = redis.call('GET', KEYS[1])
if not current or redis.call('PTTL', KEYS[1]) <= 0 then return 'missingOrExpired' end
if cjson.decode(current).version ~= ARGV[1] then return 'conflict' end
redis.call('SET', KEYS[1], ARGV[2], 'XX', 'KEEPTTL')
return 'applied'
"""


class RedisClusterStore:
    async def get_session(self, sid: str) -> Optional[Snapshot[SessionRecord]]:
        return await self._read(f"attestation:v2/{sid}", SessionRecord)

    async def set_session(self, sid: str, record: SessionRecord) -> bool:
        return await self._create(f"attestation:v2/{sid}", record, int(os.getenv("SESSION_TOKEN_TTL", "600")))

    async def update_session(self, sid: str, expected_version: str, record: SessionRecord) -> UpdateResult:
        return await self._update(f"attestation:v2/{sid}", expected_version, record)

    async def get_certificate(self, thumbprint: str) -> Optional[Snapshot[CertificateData]]:
        return await self._read(f"cert_thumb:v2:{thumbprint}", CertificateData)

    async def set_certificate(self, thumbprint: str, data: CertificateData) -> bool:
        return await self._create(f"cert_thumb:v2:{thumbprint}", data, int(os.getenv("CERT_TTL", "604800")))

    async def update_certificate(self, thumbprint: str, expected_version: str, data: CertificateData) -> UpdateResult:
        return await self._update(f"cert_thumb:v2:{thumbprint}", expected_version, data)

    async def _execute(self, command: str, key: str, action: Callable) -> Any:
        start = now_ms()
        try:
            async with open_client() as client:
                if client is None:
                    raise StorageError("Attestation storage unavailable")
                result = await action(client)
                track_redis_op(command=command, key=key, duration=now_ms() - start, success=True)
                return result
        except Exception as error:
            track_redis_op(command=command, key=key, duration=now_ms() - start, success=False)
            raise StorageError("Attestation storage unavailable") from error

    async def _read(self, key: str, record_type: type[RecordT]) -> Optional[Snapshot[RecordT]]:
        async def read(client):
            raw = await client.get(key)
            if raw is None:
                return None
            snapshot = json.loads(raw)
            version = snapshot["version"]
            if not isinstance(version, str) or not version:
                raise StorageError("Invalid versioned attestation record")
            return Snapshot(record_type(**snapshot["value"]), version)
        return await self._execute("GET", key, read)

    async def _create(self, key: str, value: RecordT, ttl: int) -> bool:
        async def create(client):
            if ttl <= 0:
                raise StorageError("Invalid attestation TTL")
            return bool(await client.set(key, json.dumps({"value": asdict(value), "version": str(uuid4())}), nx=True, ex=ttl))
        return await self._execute("SET NX", key, create)

    async def _update(self, key: str, expected_version: str, value: RecordT) -> UpdateResult:
        async def update(client):
            result = await client.eval(_CAS_SCRIPT, 1, key, expected_version,
                                       json.dumps({"value": asdict(value), "version": str(uuid4())}))
            return UpdateResult(result.decode() if isinstance(result, bytes) else result)
        return await self._execute("EVAL CAS", key, update)