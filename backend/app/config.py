from __future__ import annotations

import os
from dataclasses import dataclass
from functools import lru_cache
from urllib.parse import urlparse

PLACEHOLDER_HOSTS = {"your-service.onrender.com", "localhost", "127.0.0.1"}


def _env(name: str, default: str = "") -> str:
    return (os.getenv(name) or default).strip()


def _env_int(name: str, default: int) -> int:
    raw = _env(name)
    if not raw:
        return default
    try:
        return int(raw)
    except ValueError as exc:
        raise ValueError(f"{name} must be an integer") from exc


@dataclass(frozen=True)
class Settings:
    terabox_client_id: str
    terabox_client_secret: str
    terabox_private_secret: str
    redirect_uri: str
    app_redirect_base: str
    state_signing_secret: str
    allowed_origins: tuple[str, ...]
    timeout_seconds: int
    auth_rate_limit_per_minute: int
    enable_api_docs: bool

    @property
    def credentials_configured(self) -> bool:
        return all(
            (
                self.terabox_client_id,
                self.terabox_client_secret,
                self.terabox_private_secret,
                self.state_signing_secret,
            )
        ) and len(self.state_signing_secret) >= 32

    @property
    def configuration_errors(self) -> list[str]:
        errors: list[str] = []
        required = {
            "TERABOX_CLIENT_ID": self.terabox_client_id,
            "TERABOX_CLIENT_SECRET": self.terabox_client_secret,
            "TERABOX_PRIVATE_SECRET": self.terabox_private_secret,
            "STATE_SIGNING_SECRET": self.state_signing_secret,
        }
        for name, value in required.items():
            if not value:
                errors.append(f"{name} is not set")
        if self.state_signing_secret and len(self.state_signing_secret) < 32:
            errors.append("STATE_SIGNING_SECRET must be at least 32 characters")
        if self.redirect_uri:
            parsed = urlparse(self.redirect_uri)
            if parsed.scheme != "https" or not parsed.netloc:
                errors.append("TERABOX_REDIRECT_URI must be a public HTTPS URL when set")
        if not self.app_redirect_base.startswith("proxxquepus://"):
            errors.append("APP_REDIRECT_BASE must match the Android app deep link scheme")
        return errors


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    allowed = tuple(
        origin.strip().rstrip("/")
        for origin in _env("ALLOWED_ORIGINS").split(",")
        if origin.strip()
    )
    return Settings(
        terabox_client_id=_env("TERABOX_CLIENT_ID"),
        terabox_client_secret=_env("TERABOX_CLIENT_SECRET"),
        terabox_private_secret=_env("TERABOX_PRIVATE_SECRET"),
        redirect_uri=_env("TERABOX_REDIRECT_URI"),
        app_redirect_base=_env("APP_REDIRECT_BASE", "proxxquepus://terabox-oauth"),
        state_signing_secret=_env("STATE_SIGNING_SECRET"),
        allowed_origins=allowed,
        timeout_seconds=_env_int("TERABOX_TIMEOUT_SECONDS", 20),
        auth_rate_limit_per_minute=_env_int("AUTH_RATE_LIMIT_PER_MINUTE", 10),
        enable_api_docs=_env("ENABLE_API_DOCS", "false").lower() == "true",
    )
