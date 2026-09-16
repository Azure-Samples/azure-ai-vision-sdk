"""FastAPI entry point.

A functional twin of the React backend sample, delegating all attestation logic
to the `azure-ai-vision-face-deviceattestation` library:

  GET/POST /                       Token-generation form (resource + API key)
  GET  /native, /result           Deep-link landing pages (QR + result polling)
  GET  /api/session/result        Poll the Face service for the outcome
  POST /api/attestation/challenge
  POST /api/attestation/register
  POST /api/attestation/verify
  POST /api/session/token
  POST /api/liveness/digest
  GET  /.well-known/*             App/Universal Link binding documents

Run locally:
    uvicorn app.main:app --host 0.0.0.0 --port 8000
"""

from __future__ import annotations

import logging
import os
from contextlib import asynccontextmanager

# Load a local .env for development. In Azure App Service the values come from
# app settings (real environment variables), so this is a no-op there.
try:
    from dotenv import load_dotenv

    load_dotenv()
except Exception:  # pragma: no cover - dotenv is optional at runtime
    pass

from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles

from .api.attestation.challenge import router as attestation_challenge_router
from .api.attestation.register import router as attestation_register_router
from .api.attestation.verify import router as attestation_verify_router
from .api.liveness.digest import router as liveness_digest_router
from .api.session.result import router as session_result_router
from .api.session.token import router as session_token_router
from .request_limits import RequestBodyLimitMiddleware
from .web.index import router as index_router
from .web.session_page import router as session_page_router
from .web.wellknown import router as wellknown_router

logging.basicConfig(
    level=os.getenv("LOG_LEVEL", "INFO"),
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)


@asynccontextmanager
async def lifespan(app: FastAPI):
    # placeholder for warmup (App Insights init, etc.)
    yield


app = FastAPI(title="face-liveness-attestation-backend-python", version="0.1.0", lifespan=lifespan)
app.add_middleware(RequestBodyLimitMiddleware)

# Attestation API (delegates to the library).
app.include_router(attestation_challenge_router)
app.include_router(attestation_register_router)
app.include_router(attestation_verify_router)
app.include_router(session_token_router)
app.include_router(liveness_digest_router)
app.include_router(session_result_router)

# App/Universal Link binding documents.
app.include_router(wellknown_router)

# Web pages: index form + deep-link landing.
app.include_router(index_router)
app.include_router(session_page_router)

_STATIC_DIR = os.path.join(os.path.dirname(__file__), "web", "static")
app.mount("/static", StaticFiles(directory=_STATIC_DIR), name="static")


@app.get("/healthz")
async def healthz():
    return {"status": "ok"}
