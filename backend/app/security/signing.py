from __future__ import annotations

import base64
import hashlib
import hmac
import json
import secrets
import time
from dataclasses import dataclass


def terabox_sign(
    client_id: str,
    timestamp: int,
    client_secret: str,
    private_secret: str,
) -> str:
    """Official signature: md5("client_id_timestamp_client_secret_private_secret")."""
    raw = f"{client_id}_{timestamp}_{client_secret}_{private_secret}"
    return hashlib.md5(raw.encode("utf-8")).hexdigest()


def _b64encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).rstrip(b"=").decode("ascii")


def _b64decode(raw: str) -> bytes:
    padding = "=" * (-len(raw) % 4)
    return base64.urlsafe_b64decode(raw + padding)


def create_state(secret: str, app_redirect_base: str, ttl_seconds: int = 600) -> str:
    payload = {
        "nonce": secrets.token_urlsafe(24),
        "exp": int(time.time()) + ttl_seconds,
        "app": app_redirect_base,
    }
    encoded = _b64encode(json.dumps(payload, separators=(",", ":")).encode("utf-8"))
    signature = hmac.new(secret.encode("utf-8"), encoded.encode("ascii"), hashlib.sha256).hexdigest()
    return f"{encoded}.{signature}"


@dataclass(frozen=True)
class StateResult:
    valid: bool
    nonce: str = ""
    app_redirect_base: str = ""


def verify_state(state: str, secret: str) -> StateResult:
    if not state or "." not in state:
        return StateResult(False)
    encoded, signature = state.rsplit(".", 1)
    expected = hmac.new(secret.encode("utf-8"), encoded.encode("ascii"), hashlib.sha256).hexdigest()
    if not hmac.compare_digest(signature, expected):
        return StateResult(False)
    try:
        payload = json.loads(_b64decode(encoded))
    except (ValueError, TypeError, json.JSONDecodeError):
        return StateResult(False)
    if not isinstance(payload, dict):
        return StateResult(False)
    if int(payload.get("exp", 0)) < int(time.time()):
        return StateResult(False)
    return StateResult(True, str(payload.get("nonce", "")), str(payload.get("app", "")))
