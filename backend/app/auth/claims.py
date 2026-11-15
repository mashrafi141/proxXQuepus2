from __future__ import annotations

import time
import secrets
import threading


class ClaimStore:
    """One-time claim codes for handing short-lived tokens to the app.

    Tokens are held only in memory for a few minutes. Render Free may restart
    or sleep the instance; in that case the user must restart the connection.
    No token is written to disk or logs.
    """

    def __init__(self, ttl_seconds: int = 300):
        self.ttl_seconds = ttl_seconds
        self._claims: dict[str, tuple[float, dict]] = {}
        self._lock = threading.Lock()

    def put(self, payload: dict) -> str:
        code = secrets.token_urlsafe(32)
        with self._lock:
            self._prune()
            self._claims[code] = (time.time() + self.ttl_seconds, payload)
        return code

    def take(self, code: str) -> dict | None:
        with self._lock:
            self._prune()
            item = self._claims.pop(code, None)
        if item is None:
            return None
        expires_at, payload = item
        if expires_at < time.time():
            return None
        return payload

    def _prune(self) -> None:
        now = time.time()
        stale = [code for code, (expires, _) in self._claims.items() if expires < now]
        for code in stale:
            self._claims.pop(code, None)
