"""Session records: thin domain wrappers over the injected ClusterStore.

A session is a :class:`SessionRecord` ``{token, data}``. The store owns TTL, key
namespacing, serialization, and connection lifecycle — these helpers only add
UUID validation and telemetry. JWKS session-token validation is host glue and
lives in the sample, not here.
"""

from __future__ import annotations

import logging
import re
from dataclasses import dataclass
from typing import Any, Optional

from .logging import track_event, track_exception
from .store import ClusterStore, SessionRecord, StorageError, UpdateResult


@dataclass
class SessionData:
    token: str
    data: dict[str, Any]
    version: str

log = logging.getLogger("server_utils")

_UUID_RE = re.compile(r"^[\da-f]{8}-([\da-f]{4}-){3}[\da-f]{12}$", re.IGNORECASE)


async def save_token(store: ClusterStore, sid: str, token: str) -> Optional[str]:
    """Seed a session with its Face token and empty attestation state. Returns sid or None."""
    try:
        ok = await store.set_session(sid, SessionRecord(token=token, data={}))
        if not ok:
            return None
        log.info("Stored token with UUID: %s", sid)
        return sid
    except Exception as err:  # noqa: BLE001
        log.error("Error storing token: %s", err)
        track_exception(err, {"source": "save_token", "sid": sid})
        raise StorageError("Could not create session") from err


async def get_session_data(store: ClusterStore, sid: str) -> Optional[SessionData]:
    """Read the {token, data} record for a session. None if missing/invalid UUID."""
    if not sid or not _UUID_RE.match(sid):
        log.error("Invalid uuid format: %r", sid)
        track_event("SessionStore.GetSessionFail", {"reason": "INVALID_UUID", "sid": sid})
        return None
    try:
        snapshot = await store.get_session(sid)
        return SessionData(snapshot.value.token, snapshot.value.data, snapshot.version) if snapshot else None
    except Exception as err:  # noqa: BLE001
        log.error("Error retrieving session data: %s", err)
        track_exception(err, {"source": "get_session_data", "sid": sid})
        raise StorageError("Could not read session") from err


async def update_session_data(
    store: ClusterStore, sid: str, token: str, data: dict[str, Any], expected_version: str
) -> bool:
    """Write back token + attestation data for an existing session (store preserves TTL)."""
    if not sid or not _UUID_RE.match(sid):
        log.error("Invalid uuid format: %r", sid)
        track_event("SessionStore.UpdateFail", {"reason": "INVALID_UUID", "sid": sid})
        return False
    try:
        return await store.update_session(sid, expected_version, SessionRecord(token=token, data=data)) == UpdateResult.APPLIED
    except Exception as err:  # noqa: BLE001
        log.error("Error updating session data: %s", err)
        track_exception(err, {"source": "update_session_data", "sid": sid})
        raise StorageError("Could not update session") from err
