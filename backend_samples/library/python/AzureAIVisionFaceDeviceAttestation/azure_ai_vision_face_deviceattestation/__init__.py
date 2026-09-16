"""Public API of the attestation library — the ONLY module a host should import.

Usage:
  1. Build the service once at startup:
       svc = create_attestation_service(config, store, logger)
     providing a ``ClusterStore`` + ``AttestationLogger`` implementation and an
     ``AttestationConfig``.
  2. In each API route: parse the incoming request into the matching typed
     request (e.g. ``AttestationChallengeRequest``), ``await`` the service
     method, and serialize the returned ``HandlerOutcome``.
  3. Serve the two /.well-known documents from ``svc.apple_app_site_association()``
     / ``svc.android_asset_links()``.

Everything else under this package is internal implementation.
"""

from __future__ import annotations

# --- Construction + service surface ---
from .service import AttestationService, LivenessOutcome, create_attestation_service

# --- Injected config ---
from .config import AttestationConfig

# --- Dependencies the host implements + injects ---
from .store import CertificateData, ClusterStore, SessionRecord, Snapshot, StorageError, UpdateResult
from .logging import AttestationLogger, TelemetryProps

# --- Result shape returned by every service route method ---
from .handlers.types import ErrorBody, HandlerOutcome

# --- Per-endpoint request / body / response types (host builds the request) ---
from .handlers.challenge import (
    AttestationChallengeRequest,
    AttestationChallengeResponse,
)
from .handlers.register import (
    AttestationRegisterBody,
    AttestationRegisterData,
    AttestationRegisterRequest,
    AttestationRegisterResponse,
)
from .handlers.verify import (
    AttestationVerifyBody,
    AttestationVerifyRequest,
    AttestationVerifyResponse,
)
from .handlers.token import (
    SessionTokenBody,
    SessionTokenRequest,
    SessionTokenResponse,
)
from .handlers.digest import (
    LivenessDigestBody,
    LivenessDigestRequest,
    LivenessDigestResponse,
)

# --- Canonical route paths (use as telemetry tags in the host) ---
from .handlers.challenge import ROUTE as _CHALLENGE_ROUTE
from .handlers.register import ROUTE as _REGISTER_ROUTE
from .handlers.verify import ROUTE as _VERIFY_ROUTE
from .handlers.token import ROUTE as _SESSION_TOKEN_ROUTE
from .handlers.digest import ROUTE as _LIVENESS_DIGEST_ROUTE

ROUTES = {
    "challenge": _CHALLENGE_ROUTE,
    "register": _REGISTER_ROUTE,
    "verify": _VERIFY_ROUTE,
    "session_token": _SESSION_TOKEN_ROUTE,
    "liveness_digest": _LIVENESS_DIGEST_ROUTE,
}

__all__ = [
    "create_attestation_service",
    "AttestationService",
    "LivenessOutcome",
    "AttestationConfig",
    "ClusterStore",
    "Snapshot",
    "StorageError",
    "UpdateResult",
    "SessionRecord",
    "CertificateData",
    "AttestationLogger",
    "TelemetryProps",
    "HandlerOutcome",
    "ErrorBody",
    "AttestationChallengeRequest",
    "AttestationChallengeResponse",
    "AttestationRegisterRequest",
    "AttestationRegisterBody",
    "AttestationRegisterResponse",
    "AttestationRegisterData",
    "AttestationVerifyRequest",
    "AttestationVerifyBody",
    "AttestationVerifyResponse",
    "SessionTokenRequest",
    "SessionTokenBody",
    "SessionTokenResponse",
    "LivenessDigestRequest",
    "LivenessDigestBody",
    "LivenessDigestResponse",
    "ROUTES",
]
