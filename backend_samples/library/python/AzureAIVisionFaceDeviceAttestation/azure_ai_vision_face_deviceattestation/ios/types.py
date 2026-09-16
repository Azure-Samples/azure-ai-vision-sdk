"""
Shared types for the iOS App Attest verifier.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Literal, Optional


@dataclass
class AssertionSummary:
    authenticatorDataLength: int
    rpIdHash: str
    flags: int
    signCount: int
    signatureLength: int
    signatureEncoding: Literal["der", "ieee-p1363"]
    expectedClientDataHash: str
    signatureVerified: bool


@dataclass
class CredCertSummary:
    subject: str
    issuer: str
    serialNumber: str
    validFrom: str
    validTo: str
    thumbprint: str


@dataclass
class IntermediateCertSummary:
    subject: str
    issuer: str
    serialNumber: str
    validFrom: str
    validTo: str


@dataclass
class AppAttestVerdict:
    fmt: str
    rpIdHash: str
    appId: str
    aaguid: str
    flags: int
    signCount: int
    credentialId: str
    credentialIdMatchesPubKey: bool
    credCert: CredCertSummary
    credCertPem: str
    intermediateCert: IntermediateCertSummary
    receiptLength: int
    authDataLength: int
    nonceExtension: str
    expectedClientDataHash: str
    authCertThumbprint: str
    nonceVerified: bool
    assertion: AssertionSummary
    challengeBinding: str


@dataclass
class OngoingAssertionResult:
    ok: bool
    reason: Optional[str] = None
    message: Optional[str] = None
    signCount: Optional[int] = None
    signatureEncoding: Optional[Literal["der", "ieee-p1363"]] = None


class IosPhaseFail(Exception):
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


@dataclass
class AppAttestObject:
    fmt: str
    authData: bytes
    credCertDer: bytes
    intermediateDer: bytes
    receiptLength: int


@dataclass
class AppAttestAssertionObject:
    signature: bytes
    authenticatorData: bytes
