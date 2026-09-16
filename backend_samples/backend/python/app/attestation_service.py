"""Composition root: build the ``AttestationService`` from env + Redis/in-memory
store + host logger, and cache it as a singleton. The logger sends telemetry to
App Insights when configured and otherwise writes it to the console.

This is the ONLY place the sample reads the library's config from the
environment; the library itself is env-free.
"""

from __future__ import annotations

import os
from typing import Optional

from azure_ai_vision_face_deviceattestation import (
    AttestationConfig,
    AttestationService,
    create_attestation_service,
)

from .store import get_cluster_store
from .telemetry import make_attestation_logger


def _split_fingerprints(value: Optional[str]) -> tuple[str, ...]:
    if not value:
        return ()
    return tuple(item.strip() for item in value.replace(",", " ").split() if item.strip())


def build_config() -> AttestationConfig:
    return AttestationConfig(
        max_cert_size=int(os.getenv("MAX_CERT_SIZE", "10240")),
        android_package_name=os.getenv("ANDROID_PACKAGE_NAME", ""),
        google_service_account_json=os.getenv("GOOGLE_SERVICE_ACCOUNT_JSON", ""),
        debug_mode=os.getenv("DEBUG_MODE") == "true",
        allow_device_integrity=os.getenv("ALLOW_DEVICE_INTEGRITY") == "true",
        allow_basic_integrity=os.getenv("ALLOW_BASIC_INTEGRITY") == "true",
        allow_android_attestation_when_google_unavailable=os.getenv("ALLOW_ANDROID_ATTESTATION_WHEN_GOOGLE_UNAVAILABLE") == "true",
        ios_app_id=os.getenv("IOS_APP_ID", ""),
        ios_applink_app_id=os.getenv("IOS_APPLINK_APP_ID") or None,
        ios_app_clip_id=os.getenv("IOS_APP_CLIP_ID") or None,
        android_sha256_cert_fingerprints=_split_fingerprints(os.getenv("ANDROID_SHA256_CERT_FINGERPRINTS")),
        applink_path=os.getenv("APPLINK_PATH", "/native*"),
    )


_service: Optional[AttestationService] = None


def get_attestation_service() -> AttestationService:
    global _service
    if _service is None:
        _service = create_attestation_service(
            build_config(),
            get_cluster_store(),
            make_attestation_logger(),
        )
    return _service
