from __future__ import annotations


class AppError(Exception):
    def __init__(self, status_code: int, code: str, message: str):
        super().__init__(message)
        self.status_code = status_code
        self.code = code
        self.message = message


class TeraBoxApiError(AppError):
    def __init__(self, code: str, message: str, upstream_errno: int | None = None):
        super().__init__(502, code, message)
        self.upstream_errno = upstream_errno
