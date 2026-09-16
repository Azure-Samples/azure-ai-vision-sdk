"""
Android Key Attestation extension parsing (OID 1.3.6.1.4.1.11129.2.1.17).
"""

from __future__ import annotations

from dataclasses import dataclass
from typing import Optional

from asn1crypto import core

from ..cert_utils import get_extension_value

KEYMASTER_EXT_OID_DOTTED = "1.3.6.1.4.1.11129.2.1.17"

ANDROID_SECURITY_LEVEL_LABELS = {0: "Software", 1: "TrustedEnvironment", 2: "StrongBox"}


@dataclass
class KeyDescription:
    attestationVersion: int
    attestationSecurityLevel: int
    keyMintVersion: int
    keyMintSecurityLevel: int
    attestationChallenge: bytes


class _SecurityLevel(core.Integer):
    tag = 10


class _KeyDescriptionSequence(core.Sequence):
    _fields = [
        ("attestationVersion", core.Integer),
        ("attestationSecurityLevel", _SecurityLevel),
        ("keyMintVersion", core.Integer),
        ("keyMintSecurityLevel", _SecurityLevel),
        ("attestationChallenge", core.OctetString),
    ]


def parse_key_description(leaf_der: bytes) -> Optional[KeyDescription]:
    container = get_extension_value(leaf_der, KEYMASTER_EXT_OID_DOTTED)
    if container is None:
        return None
    try:
        fields = _KeyDescriptionSequence.load(container, strict=True)
        values = [fields[index].native for index in range(4)]
        if any(value < 0 for value in values):
            return None
        return KeyDescription(*values, attestationChallenge=fields["attestationChallenge"].native)
    except (ValueError, TypeError):
        return None


def extract_attestation_challenge_from_cert(leaf_der: bytes) -> Optional[bytes]:
    kd = parse_key_description(leaf_der)
    return kd.attestationChallenge if kd else None


def is_hardware_attestation_security_level(description: Optional[KeyDescription]) -> bool:
    return description is not None and description.attestationSecurityLevel in (1, 2)
