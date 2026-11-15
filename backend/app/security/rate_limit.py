from __future__ import annotations

import threading
import time
from collections import deque


class SlidingWindowRateLimiter:
    """Small in-memory limiter suitable for a single free-tier instance.

    Render Free does not provide persistent shared state. This limiter is a
    best-effort abuse control, not a replacement for a distributed limiter.
    """

    def __init__(self, limit: int, window_seconds: int = 60):
        self.limit = max(1, limit)
        self.window_seconds = window_seconds
        self._events: dict[str, deque[float]] = {}
        self._lock = threading.Lock()

    def allow(self, key: str) -> bool:
        now = time.time()
        cutoff = now - self.window_seconds
        with self._lock:
            bucket = self._events.setdefault(key, deque())
            while bucket and bucket[0] < cutoff:
                bucket.popleft()
            if len(bucket) >= self.limit:
                return False
            bucket.append(now)
            if len(self._events) > 2048:
                self._prune(cutoff)
            return True

    def _prune(self, cutoff: float) -> None:
        stale = [key for key, bucket in self._events.items() if not bucket or bucket[-1] < cutoff]
        for key in stale:
            self._events.pop(key, None)
