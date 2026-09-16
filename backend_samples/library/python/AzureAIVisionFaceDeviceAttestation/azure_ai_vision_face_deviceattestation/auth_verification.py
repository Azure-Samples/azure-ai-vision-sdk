"""
Auth verification dispatcher for iOS and Android attestation.
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from .logging import track_event
from .android import verify_android_auth
from .ios import verify_ios_auth


async def verify_auth_by_system(message_data: dict, attest_json: str) -> dict:
    system_lower = (message_data.get("system") or "").lower()
    track_event(
        "AuthVerification.Dispatch",
        {
            "platform": system_lower,
            "clientId": message_data.get("clientId"),
            "attestJsonLength": len(attest_json),
        },
    )

    if system_lower == "ios":
        return await verify_ios_auth(message_data, attest_json)
    if system_lower == "android":
        return await verify_android_auth(message_data, attest_json)

    track_event(
        "AuthVerification.UnsupportedSystem",
        {"platform": message_data.get("system"), "clientId": message_data.get("clientId")},
    )
    return {
        "verified": False,
        "platform": "unknown",
        "message": f"Unsupported system: {message_data.get('system')}",
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
