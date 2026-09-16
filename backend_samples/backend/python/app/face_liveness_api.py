"""Azure Face liveness-session REST calls (create + result poll).

Face resource names are restricted to letters, digits and hyphens, so the host
stays pinned to ``*.cognitiveservices.azure.com`` (prevents SSRF via a crafted
``resource`` value); callers should still validate the resource before calling in.
"""

from __future__ import annotations

import os
from typing import Any, Optional

import httpx


def _face_api_version() -> str:
    return os.getenv("FACE_API_VERSION", "v1.2")


class FaceApiError(Exception):
    """Raised for a non-2xx response from the Face service."""

    def __init__(self, message: str, status: int, body: str = "") -> None:
        super().__init__(message)
        self.status = status
        self.body = body


async def create_session(
    resource: str,
    api_key: str,
    mode: str,
    verify_image: Optional[bytes] = None,
    verify_image_name: str = "verify.jpg",
) -> dict[str, Any]:
    """Start a Face liveness session (with verify when an image is supplied)."""
    action = "detectLivenessWithVerify" if verify_image else "detectLiveness"
    endpoint = (
        f"https://{resource}.cognitiveservices.azure.com"
        f"/face/{_face_api_version()}/{action}-sessions"
    )

    async with httpx.AsyncClient(timeout=30) as client:
        if verify_image:
            files = {"verifyImage": (verify_image_name, verify_image, "application/octet-stream")}
            data = {
                "livenessOperationMode": mode,
                "enableSessionImage": "true",
                "deviceCorrelationIdSetInClient": "true",
                "deviceCorrelationIdSetInSessionStart": "true",
            }
            resp = await client.post(endpoint, headers={"Ocp-Apim-Subscription-Key": api_key}, files=files, data=data)
        else:
            resp = await client.post(
                endpoint,
                headers={"Ocp-Apim-Subscription-Key": api_key, "Content-Type": "application/json"},
                json={
                    "livenessOperationMode": mode,
                    "enableSessionImage": True,
                    "deviceCorrelationIdSetInClient": True,
                    "deviceCorrelationIdSetInSessionStart": True,
                },
            )

    if resp.status_code >= 400:
        raise FaceApiError(f"createSession failed: HTTP {resp.status_code}", resp.status_code, resp.text)
    return resp.json()


async def query_session_result(
    resource: str, api_key: str, action: str, session_id: str
) -> dict[str, Any]:
    """Fetch the liveness (and verify) result for a session."""
    endpoint = (
        f"https://{resource}.cognitiveservices.azure.com"
        f"/face/{_face_api_version()}/{action}-sessions/{session_id}"
    )
    async with httpx.AsyncClient(timeout=30) as client:
        resp = await client.get(endpoint, headers={"Ocp-Apim-Subscription-Key": api_key})
    if resp.status_code >= 400:
        raise FaceApiError(f"querySessionResult failed: HTTP {resp.status_code}", resp.status_code, resp.text)
    return resp.json()
