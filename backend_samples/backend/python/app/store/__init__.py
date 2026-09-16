"""Cluster-store selection: in-memory (dev) or Redis (default)."""

from __future__ import annotations

import os
from functools import lru_cache

from azure_ai_vision_face_deviceattestation import ClusterStore


@lru_cache(maxsize=1)
def get_cluster_store() -> ClusterStore:
    if os.getenv("USE_MEMORY_STORE") == "true":
        from .memory_store import MemoryStore

        return MemoryStore()
    from .redis_cluster_store import RedisClusterStore

    return RedisClusterStore()
