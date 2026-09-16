"""Injected configuration for the attestation library.

The library reads **no** environment variables. The host builds an
:class:`AttestationConfig` (typically from its own env / app settings) and hands
it to :func:`create_attestation_service`; internally the modules read the active
config through :func:`get_attestation_config`.

Mirrors the ``AttestationConfig`` shape of the Node.js and .NET packages.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Optional


@dataclass(frozen=True)
class AttestationConfig:
    # --- Certificate limits ---
    max_cert_size: int = 10240
    """Maximum accepted client certificate size, in bytes."""

    # --- Android Play Integrity ---
    android_package_name: str = ""
    """Android applicationId that Play Integrity + assetlinks must match."""
    google_service_account_json: str = ""
    """Serialized Google service-account JSON used to call the Play Integrity API."""
    debug_mode: bool = False
    """Loosens iOS AAGUID + Android integrity policy for development builds."""
    allow_device_integrity: bool = False
    """Accept ``MEETS_DEVICE_INTEGRITY`` from the Play Integrity verdict."""
    allow_basic_integrity: bool = False
    """Accept ``MEETS_BASIC_INTEGRITY`` from the Play Integrity verdict."""
    allow_android_attestation_when_google_unavailable: bool = False
    """Allow Android attestation to pass on hardware Key Attestation alone when the Play Integrity API is unavailable / quota-exceeded (default False = fail closed)."""

    # --- iOS App Attest ---
    ios_app_id: str = ""
    """iOS App Attest app identifier, ``TeamID.BundleID``."""

    # --- Well-known / App Link binding ---
    ios_applink_app_id: Optional[str] = None
    """AASA appID for Universal Links; falls back to :attr:`ios_app_id` when unset."""
    ios_app_clip_id: Optional[str] = None
    """Optional App Clip appID, ``TeamID.BundleID.Clip``."""
    android_sha256_cert_fingerprints: tuple[str, ...] = field(default_factory=tuple)
    """Android signing-cert SHA-256 fingerprints for the assetlinks document."""
    applink_path: str = "/native*"
    """Universal/App Link path pattern published in the binding documents."""


_config: Optional[AttestationConfig] = None


def configure_attestation(config: AttestationConfig) -> None:
    """Install the module-global config. Called by ``create_attestation_service``."""
    global _config
    _config = config


def get_attestation_config() -> AttestationConfig:
    """Return the installed config, or raise if the service was never created."""
    if _config is None:
        raise RuntimeError(
            "Attestation library is not configured; call create_attestation_service() first."
        )
    return _config
