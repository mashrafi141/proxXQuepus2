from __future__ import annotations

import logging
import re
import secrets
import time
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from .auth.claims import ClaimStore
from .config import Settings, get_settings
from .errors import AppError
from .security.rate_limit import SlidingWindowRateLimiter
from .terabox.client import TeraBoxClient
from .terabox.routes import error_response, router as auth_router

logger = logging.getLogger("proxxquepus.cloud")

SECRET_PATTERN = re.compile(
    r"(?i)(access_token|refresh_token|access_tokens|claim|code|private_secret|client_secret)=([^&\s]+)"
)


class RedactionFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        try:
            message = record.getMessage()
        except Exception:
            return True
        if SECRET_PATTERN.search(message):
            record.msg = SECRET_PATTERN.sub(r"\1=[REDACTED]", message)
            record.args = ()
        return True


def configure_logging() -> None:
    root = logging.getLogger()
    if not any(isinstance(f, RedactionFilter) for f in root.filters):
        root.addFilter(RedactionFilter())
    logging.getLogger("httpx").setLevel(logging.WARNING)
    logging.getLogger("httpcore").setLevel(logging.WARNING)


def create_app(
    settings: Settings | None = None,
    terabox_client: TeraBoxClient | None = None,
    claim_store: ClaimStore | None = None,
) -> FastAPI:
    resolved_settings = settings or get_settings()
    owns_client = terabox_client is None

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        configure_logging()
        app.state.settings = resolved_settings
        app.state.terabox_client = terabox_client or TeraBoxClient(resolved_settings)
        app.state.claim_store = claim_store or ClaimStore()
        app.state.rate_limiter = SlidingWindowRateLimiter(
            resolved_settings.auth_rate_limit_per_minute
        )
        if not resolved_settings.credentials_configured:
            logger.warning(
                "Cloud backend started without TeraBox credentials: %s",
                ", ".join(resolved_settings.configuration_errors),
            )
        yield
        if owns_client:
            await app.state.terabox_client.close()

    app = FastAPI(
        title="ProxXQuepus Cloud",
        version="1.0.2",
        docs_url="/docs" if resolved_settings.enable_api_docs else None,
        redoc_url=None,
        openapi_url="/openapi.json" if resolved_settings.enable_api_docs else None,
        lifespan=lifespan,
    )

    if resolved_settings.allowed_origins:
        app.add_middleware(
            CORSMiddleware,
            allow_origins=list(resolved_settings.allowed_origins),
            allow_credentials=False,
            allow_methods=["GET", "POST"],
            allow_headers=["Content-Type", "X-Request-Id"],
        )

    @app.middleware("http")
    async def security_headers(request: Request, call_next):
        request_id = request.headers.get("X-Request-Id") or secrets.token_hex(8)
        request.state.request_id = request_id
        started = time.perf_counter()
        try:
            response = await call_next(request)
        except Exception as exc:  # never log exception text (may contain URLs/tokens)
            logger.error(
                "Unhandled request failure request_id=%s type=%s",
                request_id,
                type(exc).__name__,
            )
            return error_response(
                AppError(500, "INTERNAL_ERROR", "The cloud service encountered an error"),
                request_id,
            )
        elapsed_ms = int((time.perf_counter() - started) * 1000)
        response.headers["X-Request-Id"] = request_id
        response.headers["X-Content-Type-Options"] = "nosniff"
        response.headers["Referrer-Policy"] = "no-referrer"
        response.headers["Cache-Control"] = response.headers.get(
            "Cache-Control", "no-store"
        )
        logger.info(
            "request method=%s path=%s status=%s duration_ms=%s request_id=%s",
            request.method,
            request.url.path,
            response.status_code,
            elapsed_ms,
            request_id,
        )
        return response

    @app.exception_handler(AppError)
    async def app_error_handler(request: Request, exc: AppError):
        request_id = getattr(request.state, "request_id", None)
        return error_response(exc, request_id)

    @app.get("/health")
    async def health() -> JSONResponse:
        return JSONResponse(
            {
                "status": "ok",
                "service": "proxxquepus-cloud",
                "version": "1.0.2",
                "configured": resolved_settings.credentials_configured,
            }
        )

    app.include_router(auth_router)
    return app


app = create_app()
