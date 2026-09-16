"""AttestationService — the single object a host builds and calls.

``create_attestation_service(config, store, logger)`` installs the injected
config + logger and returns a service whose methods map 1:1 to the attestation
endpoints, plus a few session / well-known helpers. Mirrors the Node / .NET
service surface.
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Awaitable, Optional

from .config import AttestationConfig, configure_attestation
from .handlers.challenge import AttestationChallengeRequest, handle_challenge
from .handlers.digest import LivenessDigestRequest, handle_liveness_digest
from .handlers.register import AttestationRegisterRequest, handle_register
from .handlers.token import SessionTokenRequest, handle_session_token
from .handlers.types import HandlerOutcome, fail
from .handlers.verify import AttestationVerifyRequest, handle_verify
from .logging import AttestationLogger, set_attestation_logger
from .server_utils import get_session_data, save_token
from .store import ClusterStore, StorageError
from .well_known import apple_app_site_association as _apple_app_site_association
from .well_known import assetlinks as _assetlinks


@dataclass
class LivenessOutcome:
    completed: bool
    client_digest: Optional[str] = None


class AttestationService:
    """Endpoint methods + session / well-known helpers, over an injected store."""

    def __init__(self, store: ClusterStore) -> None:
        self._store = store

    async def challenge(self, req: AttestationChallengeRequest) -> HandlerOutcome:
        return await self._with_storage_failure(handle_challenge(req, self._store))

    async def register(self, req: AttestationRegisterRequest) -> HandlerOutcome:
        return await self._with_storage_failure(handle_register(req, self._store))

    async def verify(self, req: AttestationVerifyRequest) -> HandlerOutcome:
        return await self._with_storage_failure(handle_verify(req, self._store))

    async def session_token(self, req: SessionTokenRequest) -> HandlerOutcome:
        return await self._with_storage_failure(handle_session_token(req, self._store))

    async def liveness_digest(self, req: LivenessDigestRequest) -> HandlerOutcome:
        return await self._with_storage_failure(handle_liveness_digest(req, self._store))

    async def _with_storage_failure(self, action: Awaitable[HandlerOutcome]) -> HandlerOutcome:
        try:
            return await action
        except StorageError:
            return fail("attestation", 503, "STORAGE_UNAVAILABLE", "Attestation storage unavailable")

    async def session_exists(self, sid: str) -> bool:
        record = await get_session_data(self._store, sid)
        return record is not None

    async def get_liveness_outcome(self, sid: str) -> LivenessOutcome:
        record = await get_session_data(self._store, sid)
        if not record:
            return LivenessOutcome(completed=False)
        data = record.data
        return LivenessOutcome(
            completed=bool(data.get("digestCompleted")),
            client_digest=data.get("digest"),
        )

    async def save_session(self, sid: str, token: str) -> Optional[str]:
        return await save_token(self._store, sid, token)

    def apple_app_site_association(self) -> dict:
        return _apple_app_site_association()

    def android_asset_links(self) -> list:
        return _assetlinks()


def create_attestation_service(
    config: AttestationConfig,
    store: ClusterStore,
    logger: Optional[AttestationLogger] = None,
) -> AttestationService:
    """Build the service once at startup; cache the result as a singleton."""
    configure_attestation(config)
    if logger is not None:
        set_attestation_logger(logger)
    return AttestationService(store)
