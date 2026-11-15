from __future__ import annotations

from pydantic import BaseModel, Field


class AuthStartResponse(BaseModel):
    authorization_url: str
    state: str
    expires_in: int


class ExchangeRequest(BaseModel):
    code: str = Field(min_length=1, max_length=4096)
    state: str = Field(min_length=1, max_length=4096)


class ClaimRequest(BaseModel):
    claim_code: str = Field(min_length=16, max_length=256)


class RefreshRequest(BaseModel):
    refresh_token: str = Field(min_length=8, max_length=4096)


class AccountProfile(BaseModel):
    id: str
    display_name: str
    avatar_url: str | None = None
    vip_type: int = 0
    user_id: str | None = None


class TokenPayload(BaseModel):
    access_token: str
    refresh_token: str
    expires_in: int
    api_domain: str
    upload_domain: str | None = None
    account: AccountProfile
    state: str | None = None


class ErrorBody(BaseModel):
    code: str
    message: str


class ErrorResponse(BaseModel):
    error: ErrorBody
    request_id: str | None = None
