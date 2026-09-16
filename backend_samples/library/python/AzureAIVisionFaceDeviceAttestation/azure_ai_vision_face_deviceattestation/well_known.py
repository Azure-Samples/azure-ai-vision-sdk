"""App Link / Universal Link binding documents, built from ``AttestationConfig``.

Config-driven and env-free: the host supplies ``ios_applink_app_id`` /
``ios_app_id`` / ``ios_app_clip_id`` / ``android_package_name`` /
``android_sha256_cert_fingerprints`` / ``applink_path`` via the injected config,
so a fresh deployment only changes settings to bind a new app identity.
"""

from __future__ import annotations

from typing import Any

from .config import get_attestation_config


def applink_path() -> str:
    return get_attestation_config().applink_path or "/native*"


def apple_app_site_association() -> dict[str, Any]:
    """Build the AASA document iOS fetches from /.well-known to bind the domain."""
    cfg = get_attestation_config()
    app_id = (cfg.ios_applink_app_id or cfg.ios_app_id or "").strip()
    clip_id = (cfg.ios_app_clip_id or "").strip()

    app_ids: list[str] = []
    if app_id:
        app_ids.append(app_id)
    if clip_id:
        app_ids.append(clip_id)

    doc: dict[str, Any] = {
        "applinks": {
            "details": [
                {
                    "appIDs": app_ids,
                    "components": [
                        {
                            "/": applink_path(),
                            "comment": "Opens the app for QuickLink liveness sessions.",
                        }
                    ],
                }
            ]
        }
    }
    if clip_id:
        doc["appclips"] = {"apps": [clip_id]}
    return doc


def assetlinks() -> list[dict[str, Any]]:
    """Build the Digital Asset Links document Android fetches to bind the domain."""
    cfg = get_attestation_config()
    package_name = (cfg.android_package_name or "").strip()
    fingerprints = list(cfg.android_sha256_cert_fingerprints)

    return [
        {
            "relation": ["delegate_permission/common.handle_all_urls"],
            "target": {
                "namespace": "android_app",
                "package_name": package_name,
                "sha256_cert_fingerprints": fingerprints,
            },
        }
    ]
