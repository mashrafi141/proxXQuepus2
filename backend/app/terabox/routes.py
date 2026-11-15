from __future__ import annotations

from urllib.parse import urlencode

from fastapi import APIRouter, Request
from fastapi.responses import JSONResponse, RedirectResponse

from ..errors import AppError
from ..security.signing import create_state, verify_state
from .models import (
    AuthStartResponse,
    ClaimRequest,
    ErrorResponse,
    ExchangeRequest,
    RefreshRequest,
    TokenPayload,
)
from .service import TeraBoxAuthService

router = APIRouter(prefix="/v1/auth", tags=["auth"])


def _settings(request: Request):
    return request.app.state.settings


def _client(request: Request):
    return request.app.state.terabox_client


def _claims(request: Request):
    return request.app.state.claim_store


def _rate_limit(request: Request, scope: str) -> None:
    limiter = request.app.state.rate_limiter
    host = request.client.host if request.client else "unknown"
    if not limiter.allow(f"{scope}:{host}"):
        raise AppError(429, "RATE_LIMITED", "Too many requests; retry later")


def _require_configured(request: Request) -> None:
    settings = _settings(request)
    if not settings.credentials_configured:
        raise AppError(
            503,
            "SERVER_NOT_CONFIGURED",
            "TeraBox developer credentials and backend secrets are not configured",
        )


def _error_redirect(request: Request, base: str, code: str, state: str) -> RedirectResponse:
    url = f"{base}?{urlencode({'status': 'error', 'error': code, 'state': state})}"
    response = RedirectResponse(url=url, status_code=302)
    response.headers["Cache-Control"] = "no-store"
    return response


@router.get("/status")
async def auth_status(request: Request) -> JSONResponse:
    settings = _settings(request)
    response = JSONResponse(
        {
            "configured": settings.credentials_configured,
            "authorization_host": "https://www.terabox.com",
            "app_redirect_base": settings.app_redirect_base,
        }
    )
    response.headers["Cache-Control"] = "no-store"
    return response


@router.get("/start", response_model=AuthStartResponse)
async def start_authorization(request: Request) -> JSONResponse:
    _require_configured(request)
    _rate_limit(request, "start")
    settings = _settings(request)
    state = create_state(settings.state_signing_secret, settings.app_redirect_base)
    authorization_url = (
        "https://www.terabox.com/wap/outside/login"
        f"?clientId={settings.terabox_client_id}&isFromApp=1"
    )
    response = JSONResponse(
        AuthStartResponse(
            authorization_url=authorization_url,
            state=state,
            expires_in=600,
        ).model_dump()
    )
    response.headers["Cache-Control"] = "no-store"
    return response


@router.get("/callback")
async def oauth_callback(request: Request):
    settings = _settings(request)
    state = request.query_params.get("state", "")
    code = request.query_params.get("code", "")
    upstream_error = request.query_params.get("error", "")

    state_result = verify_state(state, settings.state_signing_secret)
    if not state_result.valid or state_result.app_redirect_base != settings.app_redirect_base:
        return _error_redirect(
            request, settings.app_redirect_base, "INVALID_OAUTH_STATE", state
        )
    if not settings.credentials_configured:
        return _error_redirect(
            request, settings.app_redirect_base, "SERVER_NOT_CONFIGURED", state
        )
    if upstream_error or not code:
        return _error_redirect(
            request, settings.app_redirect_base, "AUTHORIZATION_DENIED", state
        )

    try:
        service = TeraBoxAuthService(settings, _client(request))
        payload = await service.complete_authorization(code, state=state)
    except AppError as exc:
        return _error_redirect(request, settings.app_redirect_base, exc.code, state)

    claim_code = _claims(request).put(payload.model_dump())
    url = (
        f"{settings.app_redirect_base}?"
        f"{urlencode({'status': 'ok', 'claim': claim_code, 'state': state})}"
    )
    response = RedirectResponse(url=url, status_code=302)
    response.headers["Cache-Control"] = "no-store"
    return response


@router.post("/exchange", response_model=TokenPayload)
async def exchange_code(request: Request, body: ExchangeRequest) -> JSONResponse:
    """Android authorization-code flow.

    The official Android flow returns the one-time `code` directly to the app's
    registered custom URL scheme. The app sends it here together with the signed
    state it received from /start. This keeps the confidential exchange on the
    server and prevents an unsolicited custom-scheme intent from being accepted
    without the app's pending state.
    """
    _require_configured(request)
    _rate_limit(request, "exchange")
    settings = _settings(request)
    state_result = verify_state(body.state, settings.state_signing_secret)
    if not state_result.valid or state_result.app_redirect_base != settings.app_redirect_base:
        raise AppError(401, "INVALID_OAUTH_STATE", "The authorization state is invalid or expired")
    service = TeraBoxAuthService(settings, _client(request))
    payload = await service.complete_authorization(body.code, state=body.state)
    response = JSONResponse(payload.model_dump())
    response.headers["Cache-Control"] = "no-store"
    return response


@router.post("/claim", response_model=TokenPayload)
async def claim_tokens(request: Request, body: ClaimRequest) -> JSONResponse:
    _rate_limit(request, "claim")
    payload = _claims(request).take(body.claim_code)
    if payload is None:
        raise AppError(
            404,
            "CLAIM_NOT_FOUND",
            "The authorization result expired or was already used. Please reconnect.",
        )
    response = JSONResponse(payload)
    response.headers["Cache-Control"] = "no-store"
    return response


@router.post("/refresh", response_model=TokenPayload)
async def refresh_tokens(request: Request, body: RefreshRequest) -> JSONResponse:
    _require_configured(request)
    _rate_limit(request, "refresh")
    settings = _settings(request)
    try:
        service = TeraBoxAuthService(settings, _client(request))
        payload = await service.refresh(body.refresh_token)
    except AppError as exc:
        if exc.code in {"REFRESH_TOKEN_INVALID", "REFRESH_TOKEN_EXPIRED", "TOKEN_INVALID"}:
            raise AppError(
                401, "REAUTH_REQUIRED", "The TeraBox connection has expired; reconnect the account"
            ) from exc
        raise
    response = JSONResponse(payload.model_dump())
    response.headers["Cache-Control"] = "no-store"
    return response


def error_response(exc: AppError, request_id: str | None = None) -> JSONResponse:
    return JSONResponse(
        status_code=exc.status_code,
        content=ErrorResponse(
            error={"code": exc.code, "message": exc.message},
            request_id=request_id,
        ).model_dump(),
        headers={"Cache-Control": "no-store"},
    )
