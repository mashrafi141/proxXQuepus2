from __future__ import annotations

from typing import Any

from ..config import Settings
from ..errors import AppError
from .client import TeraBoxClient, normalize_api_domain
from .models import AccountProfile, TokenPayload


class TeraBoxAuthService:
    def __init__(self, settings: Settings, client: TeraBoxClient):
        self.settings = settings
        self.client = client

    async def _build_token_payload(
        self,
        data: dict[str, Any],
        *,
        state: str | None = None,
        fallback_account: AccountProfile | None = None,
    ) -> TokenPayload:
        access_token = str(data.get("access_token", ""))
        refresh_token = str(data.get("refresh_token", ""))
        if not access_token or not refresh_token:
            raise AppError(502, "TOKEN_EXCHANGE_FAILED", "TeraBox did not return valid tokens")
        token_info = await self.client.token_info(access_token)
        api_domain = normalize_api_domain(str(token_info.get("api_domain", "")))
        user_id = str(token_info.get("user_id", "") or "")
        profile = fallback_account
        try:
            info = await self.client.user_info(access_token, api_domain)
            uk = str(info.get("uk", "") or user_id)
            profile = AccountProfile(
                id=uk or user_id,
                display_name=str(info.get("uname", "") or "TeraBox account"),
                avatar_url=str(info.get("avatar_url", "") or "") or None,
                vip_type=_safe_int(info.get("vip_type")),
                user_id=user_id or None,
            )
        except AppError:
            if profile is None:
                profile = AccountProfile(
                    id=user_id or "terabox-account",
                    display_name="TeraBox account",
                    user_id=user_id or None,
                )
        return TokenPayload(
            access_token=access_token,
            refresh_token=refresh_token,
            expires_in=_safe_int(data.get("expires_in"), default=172800),
            api_domain=api_domain,
            upload_domain=str(token_info.get("upload_domain", "") or "") or None,
            account=profile,
            state=state,
        )

    async def complete_authorization(
        self, code: str, state: str | None = None
    ) -> TokenPayload:
        data = await self.client.exchange_code(code)
        return await self._build_token_payload(data, state=state)

    async def refresh(self, refresh_token: str) -> TokenPayload:
        data = await self.client.refresh_tokens(refresh_token)
        return await self._build_token_payload(data)


def _safe_int(value: Any, default: int = 0) -> int:
    try:
        return int(value)
    except (TypeError, ValueError):
        return default
