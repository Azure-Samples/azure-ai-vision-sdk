"""Serves the Universal Link (iOS) and App Link (Android) binding documents.

Both files MUST be served from the site root under /.well-known with a JSON
content type and no redirect. The document contents come from the attestation
library's config-driven builders, so a fresh deployment only needs app settings
to bind a new app identity.
"""

from __future__ import annotations

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from ..attestation_service import get_attestation_service

router = APIRouter()

_CACHE = {"Cache-Control": "public, max-age=3600"}


@router.get("/.well-known/apple-app-site-association")
async def apple_app_site_association_route():
    return JSONResponse(
        content=get_attestation_service().apple_app_site_association(),
        media_type="application/json",
        headers=_CACHE,
    )


# Some Apple tooling also probes the legacy root-level path.
@router.get("/apple-app-site-association")
async def apple_app_site_association_legacy_route():
    return JSONResponse(
        content=get_attestation_service().apple_app_site_association(),
        media_type="application/json",
        headers=_CACHE,
    )


@router.get("/.well-known/assetlinks.json")
async def assetlinks_route():
    return JSONResponse(
        content=get_attestation_service().android_asset_links(),
        media_type="application/json",
        headers=_CACHE,
    )
