"""Redis (redis.asyncio) connection + dependency-telemetry helpers.

Local dev: ``USE_LOCAL_REDIS=true`` -> ``redis://127.0.0.1:6379`` (no auth/TLS).
Production: Azure Managed Redis with Entra / Managed Identity — the username is
the ``oid`` claim from the AAD token, the password is the token itself.
"""

from __future__ import annotations

import base64
import json
import logging
import os
import time
from contextlib import asynccontextmanager
from typing import Any, Optional

import redis.asyncio as redis

from ..telemetry.app_insights_server import track_dependency

log = logging.getLogger("redis_client")


def _redis_target() -> str:
    if os.getenv("USE_LOCAL_REDIS") == "true":
        return "127.0.0.1:6379"
    host = os.getenv("REDIS_HOSTNAME") or "127.0.0.1"
    port = os.getenv("REDIS_PORT") or "6380"
    return f"{host}:{port}"


def track_redis_op(
    *,
    command: str,
    key: Optional[str],
    duration: float,
    success: bool,
    result_code: Any = None,
    properties: Optional[dict[str, Any]] = None,
) -> None:
    track_dependency(
        name=f"Redis.{command}",
        target=_redis_target(),
        data=f"{command} {key}" if key else command,
        duration=duration,
        success=success,
        result_code=result_code if result_code is not None else ("OK" if success else "ERROR"),
        dependency_type_name="Redis",
        properties=properties,
    )


def _extract_username_from_token(token: str) -> Optional[str]:
    try:
        payload = token.split(".")[1]
        padded = payload + "=" * ((4 - len(payload) % 4) % 4)
        decoded = base64.urlsafe_b64decode(padded.encode("ascii"))
        return json.loads(decoded).get("oid")
    except Exception as e:  # noqa: BLE001
        log.error("Failed to extract OID from AAD token: %s", e)
        return None


async def create_redis_client() -> Optional["redis.Redis"]:
    """Return a connected redis.asyncio client, or None on config / auth error."""
    if os.getenv("USE_LOCAL_REDIS") == "true":
        try:
            client = redis.Redis(host="127.0.0.1", port=6379, decode_responses=True, socket_connect_timeout=5)
            await client.ping()
            return client
        except Exception as e:  # noqa: BLE001
            log.error("Local Redis connect failed: %s", e)
            return None

    host = os.getenv("REDIS_HOSTNAME")
    if not host:
        log.error("REDIS_HOSTNAME is not set")
        return None
    try:
        port = int(os.getenv("REDIS_PORT") or "6380")
    except ValueError:
        log.error("Invalid REDIS_PORT: %r", os.getenv("REDIS_PORT"))
        return None
    if port <= 0:
        return None

    try:
        from azure.identity import ManagedIdentityCredential

        credential = ManagedIdentityCredential()
        token = credential.get_token("https://redis.azure.com/.default")
        if not token or not token.token:
            log.error("Got an empty AAD token for Redis")
            return None
        username = _extract_username_from_token(token.token)
        if not username:
            log.error("OID missing from AAD token")
            return None
        client = redis.Redis(
            host=host, port=port, username=username, password=token.token,
            ssl=True, decode_responses=True, socket_connect_timeout=10,
        )
        await client.ping()
        return client
    except Exception as e:  # noqa: BLE001
        log.error("Failed to create Redis client: %s", e)
        return None


async def close_client(client: "redis.Redis") -> None:
    try:
        await client.aclose()
    except AttributeError:
        await client.close()  # redis < 5.0.1
    except Exception:  # noqa: BLE001
        pass


@asynccontextmanager
async def open_client():
    """Yield a connected client (or None) and close it on exit."""
    client = await create_redis_client()
    try:
        yield client
    finally:
        if client is not None:
            await close_client(client)


def now_ms() -> float:
    return time.time() * 1000
