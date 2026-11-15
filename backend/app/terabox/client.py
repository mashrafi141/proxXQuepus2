from __future__ import annotations

import asyncio
import time
from typing import Any
from urllib.parse import urlparse

import httpx

from ..config import Settings
from ..errors import TeraBoxApiError
from ..security.signing import terabox_sign

AUTHORIZATION_HOST = "https://www.terabox.com"

ERROR_MESSAGES: dict[int, tuple[str, str]] = {
    2: ("BAD_REQUEST", "Required parameters are missing"),
    100001: ("SERVER_CONFIGURATION_ERROR", "The TeraBox application credentials are invalid"),
    100002: ("AUTH_CODE_INVALID", "The authorization code is invalid or expired"),
    200001: ("UNSUPPORTED_GRANT", "The authorization grant type is not supported"),
    300001: ("RATE_LIMITED", "Too many authorization requests; retry later"),
    400001: ("AUTHORIZATION_PENDING", "The user has not completed authorization"),
    500001: ("UPSTREAM_ERROR", "TeraBox reported an internal error"),
    200002: ("TOKEN_INVALID", "The TeraBox access token is invalid"),
    200003: ("TOKEN_EXPIRED", "The TeraBox access token has expired"),
    200004: ("REFRESH_TOKEN_INVALID", "The TeraBox refresh token is invalid"),
    200005: ("REFRESH_TOKEN_EXPIRED", "The TeraBox refresh token has expired"),
}


def normalize_api_domain(domain: str) -> str:
    value = (domain or "").strip().rstrip("/")
    if not value:
        return AUTHORIZATION_HOST
    if "://" not in value:
        value = f"https://{value}"
    parsed = urlparse(value)
    if parsed.scheme != "https" or not parsed.netloc:
        raise TeraBoxApiError("UPSTREAM_ERROR", "TeraBox returned an invalid API domain")
    return f"{parsed.scheme}://{parsed.netloc}"


class TeraBoxClient:
    def __init__(self, settings: Settings, http: httpx.AsyncClient | None = None):
        self.settings = settings
        self._owns_http = http is None
        self.http = http or httpx.AsyncClient(
            timeout=httpx.Timeout(settings.timeout_seconds),
            follow_redirects=False,
            headers={"Accept": "application/json", "User-Agent": "ProxXQuepus-Cloud/1.0.2"},
        )

    async def close(self) -> None:
        if self._owns_http:
            await self.http.aclose()

    async def _request_json(
        self,
        method: str,
        url: str,
        *,
        data: dict[str, str] | None = None,
        params: dict[str, str] | None = None,
    ) -> dict[str, Any]:
        last_error: Exception | None = None
        for attempt in range(3):
            try:
                response = await self.http.request(method, url, data=data, params=params)
                if response.status_code == 429 or response.status_code >= 500:
                    last_error = TeraBoxApiError("UPSTREAM_UNAVAILABLE", "TeraBox is temporarily unavailable")
                    if attempt < 2:
                        await asyncio.sleep(0.4 * (2**attempt))
                        continue
                    raise last_error
                if response.status_code in (401, 403):
                    raise TeraBoxApiError("TOKEN_INVALID", "TeraBox rejected the authorization")
                if response.status_code >= 400:
                    raise TeraBoxApiError("UPSTREAM_ERROR", "TeraBox rejected the request")
                try:
                    payload = response.json()
                except ValueError as exc:
                    raise TeraBoxApiError("UPSTREAM_ERROR", "TeraBox returned an invalid response") from exc
                if not isinstance(payload, dict):
                    raise TeraBoxApiError("UPSTREAM_ERROR", "TeraBox returned an invalid response")
                errno = payload.get("errno")
                if errno is None:
                    errno = payload.get("code")
                if errno is None:
                    errno = payload.get("error_code")
                if errno in (0, "0", None):
                    return payload
                try:
                    numeric = int(errno)
                except (TypeError, ValueError):
                    numeric = -1
                code, message = ERROR_MESSAGES.get(numeric, ("UPSTREAM_ERROR", "TeraBox rejected the request"))
                raise TeraBoxApiError(code, message, numeric)
            except (httpx.TimeoutException, httpx.NetworkError) as exc:
                last_error = TeraBoxApiError("UPSTREAM_UNAVAILABLE", "TeraBox is unreachable")
                if attempt < 2:
                    await asyncio.sleep(0.4 * (2**attempt))
                    continue
                raise last_error from exc
        raise last_error or TeraBoxApiError("UPSTREAM_ERROR", "TeraBox request failed")

    def _signed_form(self) -> dict[str, str]:
        timestamp = int(time.time())
        return {
            "client_id": self.settings.terabox_client_id,
            "client_secret": self.settings.terabox_client_secret,
            "timestamp": str(timestamp),
            "sign": terabox_sign(
                self.settings.terabox_client_id,
                timestamp,
                self.settings.terabox_client_secret,
                self.settings.terabox_private_secret,
            ),
        }

    async def exchange_code(self, code: str) -> dict[str, Any]:
        form = self._signed_form()
        form["grant_type"] = "authorization_code"
        form["code"] = code
        payload = await self._request_json(
            "POST", f"{AUTHORIZATION_HOST}/oauth/gettoken", data=form
        )
        data = payload.get("data")
        if not isinstance(data, dict) or not data.get("access_token"):
            raise TeraBoxApiError("TOKEN_EXCHANGE_FAILED", "TeraBox did not return an access token")
        return data

    async def refresh_tokens(self, refresh_token: str) -> dict[str, Any]:
        form = self._signed_form()
        form["refresh_token"] = refresh_token
        payload = await self._request_json(
            "POST", f"{AUTHORIZATION_HOST}/oauth/refreshtoken", data=form
        )
        data = payload.get("data")
        if not isinstance(data, dict) or not data.get("access_token"):
            raise TeraBoxApiError("TOKEN_REFRESH_FAILED", "TeraBox did not return an access token")
        return data

    async def token_info(self, access_token: str) -> dict[str, Any]:
        payload = await self._request_json(
            "POST",
            f"{AUTHORIZATION_HOST}/oauth/tokeninfo",
            data={"access_token": access_token},
        )
        data = payload.get("data")
        if not isinstance(data, dict) or not data.get("api_domain"):
            raise TeraBoxApiError("TOKEN_INFO_FAILED", "TeraBox did not return token information")
        data["api_domain"] = normalize_api_domain(str(data["api_domain"]))
        return data

    async def user_info(self, access_token: str, api_domain: str) -> dict[str, Any]:
        base = normalize_api_domain(api_domain)
        payload = await self._request_json(
            "GET",
            f"{base}/openapi/uinfo",
            params={"access_tokens": access_token},
        )
        return payload
