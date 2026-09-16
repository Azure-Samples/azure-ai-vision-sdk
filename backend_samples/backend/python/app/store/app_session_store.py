"""App-owned per-session context.

Holds the sample's own Face resource + API key + action so `/api/session/result`
can poll the liveness outcome later. Stored under a SEPARATE key (`session/<sid>`)
from the attestation library's session record, so the library never sees these
credentials. Uses Redis, or an in-process dict when ``USE_MEMORY_STORE=true``.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass
from typing import Optional

from .redis_client import now_ms, open_client, track_redis_op

_APP_SESSION_PREFIX = "session/"


@dataclass
class AppSession:
    resource: str
    api_key: str
    action: str


def _ttl_seconds() -> int:
    return int(os.getenv("SESSION_TOKEN_TTL", "600"))


def _key(sid: str) -> str:
    return f"{_APP_SESSION_PREFIX}{sid}"


def _use_memory() -> bool:
    return os.getenv("USE_MEMORY_STORE") == "true"


_mem: dict[str, AppSession] = {}


async def save_app_session(sid: str, data: AppSession) -> bool:
    if _use_memory():
        _mem[sid] = data
        return True
    key = _key(sid)
    start = now_ms()
    async with open_client() as client:
        if client is None:
            track_redis_op(command="SET", key=key, duration=now_ms() - start, success=False, result_code="CLIENT_CREATE_FAIL", properties={"op": "saveAppSession"})
            return False
        try:
            value = json.dumps({"resource": data.resource, "apiKey": data.api_key, "action": data.action})
            await client.set(key, value, ex=_ttl_seconds())
            track_redis_op(command="SET", key=key, duration=now_ms() - start, success=True, properties={"op": "saveAppSession", "ttlSec": _ttl_seconds()})
            return True
        except Exception:  # noqa: BLE001
            track_redis_op(command="SET", key=key, duration=now_ms() - start, success=False, result_code="EXCEPTION", properties={"op": "saveAppSession"})
            return False


async def get_app_session(sid: str) -> Optional[AppSession]:
    if _use_memory():
        return _mem.get(sid)
    key = _key(sid)
    start = now_ms()
    async with open_client() as client:
        if client is None:
            track_redis_op(command="GET", key=key, duration=now_ms() - start, success=False, result_code="CLIENT_CREATE_FAIL", properties={"op": "getAppSession"})
            return None
        try:
            raw = await client.get(key)
            if not raw:
                track_redis_op(command="GET", key=key, duration=now_ms() - start, success=True, result_code="MISS", properties={"op": "getAppSession"})
                return None
            track_redis_op(command="GET", key=key, duration=now_ms() - start, success=True, result_code="HIT", properties={"op": "getAppSession"})
            d = json.loads(raw)
            return AppSession(resource=d.get("resource", ""), api_key=d.get("apiKey", ""), action=d.get("action", ""))
        except Exception:  # noqa: BLE001
            track_redis_op(command="GET", key=key, duration=now_ms() - start, success=False, result_code="EXCEPTION", properties={"op": "getAppSession"})
            return None
