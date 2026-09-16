"""Base64 helpers for accepting both standard and URL-safe wire values."""

from __future__ import annotations

import base64
import binascii


def decode_base64(value: str) -> bytes:
    """Decode padded or unpadded standard/URL-safe Base64."""
    if not isinstance(value, str):
        raise TypeError("Base64 value must be a string")

    normalized = value.replace("-", "+").replace("_", "/")
    normalized += "=" * (-len(normalized) % 4)
    try:
        return base64.b64decode(normalized, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValueError("Invalid Base64 value") from exc
