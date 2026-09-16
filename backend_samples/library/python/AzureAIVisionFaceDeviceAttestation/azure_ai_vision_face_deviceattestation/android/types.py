"""
Shared types for the Android Key Attestation + Play Integrity verifier.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Optional


@dataclass
class AndroidAttestationJson:
    token: str
    certificateChain: list[str]  # base64-encoded DER certificates


@dataclass
class PlayIntegrityVerdict:
    requestDetails: Optional[dict] = None
    accountDetails: Optional[dict] = None
    appIntegrity: Optional[dict] = None
    deviceIntegrity: Optional[dict] = None
    environmentDetails: Optional[dict] = None
    attestationChallenge: Optional[str] = None


class AndroidPhaseFail(Exception):
    """Failure signal returned by phase helpers."""

    def __init__(
        self,
        reason: str,
        message: str,
        extras: Optional[dict[str, Any]] = None,
        extra_props: Optional[dict[str, Any]] = None,
    ):
        super().__init__(message)
        self.reason = reason
        self.message = message
        self.extras = extras or {}
        self.extra_props = extra_props or {}
