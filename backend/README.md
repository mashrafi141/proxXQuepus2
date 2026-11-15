# ProxXQuepus Cloud Backend

Lightweight FastAPI service for TeraBox Open Platform OAuth and token refresh. It intentionally does **not** proxy media or metadata.

## Endpoints

- `GET /health` — service status and whether official credentials are configured.
- `GET /v1/auth/status` — safe configuration summary (no secrets).
- `GET /v1/auth/start` — creates a signed OAuth state and returns the official TeraBox login URL.
- `POST /v1/auth/exchange` — `{"code":"...","state":"..."}`; official Android code flow. Validates the signed state and returns tokens/account over HTTPS.
- `GET /v1/auth/callback` — optional HTTP callback/web flow; exchanges the code, stores a one-time claim in memory, redirects to the Android deep link.
- `POST /v1/auth/claim` — `{"claim_code":"..."}`; returns tokens and account once for the optional HTTP callback flow.
- `POST /v1/auth/refresh` — `{"refresh_token":"..."}`; rotates tokens using the confidential backend signature.

## Local run

```bash
cd backend
python -m venv .venv
. .venv/bin/activate            # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env
# Fill in official TeraBox credentials and a random STATE_SIGNING_SECRET.
uvicorn app.main:app --reload --port 8000
```

`ENABLE_API_DOCS=true` exposes `/docs` for local development only. Leave it false in production.

## Security notes

- `TERABOX_CLIENT_SECRET` and `TERABOX_PRIVATE_SECRET` are server-only. Never put them in the APK, a `.env` committed to Git, or documentation.
- The official Android flow returns the one-time `code` to the app custom scheme; the app posts it to `/v1/auth/exchange` together with its pending signed state. A custom-scheme intent without that app-held state is rejected.
- OAuth state is HMAC-signed with an expiry.
- Claim codes (optional HTTP callback flow) are random, one-time, memory-only and expire after five minutes.
- Tokens are never written to disk on Render and are never logged. Uvicorn access logs are disabled in the Render start command; use `--no-access-log` locally too if URL query strings may contain sensitive values.
- CORS is disabled unless `ALLOWED_ORIGINS` is explicitly set. Native Android does not need CORS.
- The rate limiter is in-memory and best-effort on a single free instance. It is not a distributed rate limiter.

## Render Free limitations

- The service may sleep/cold-start.
- There is no persistent disk or database in this design.
- A pending optional HTTP-callback claim is lost if the process restarts. The Android `/v1/auth/exchange` flow is stateless on the backend, so it is not affected by a restart between code return and exchange.
- Do not add a ping/keep-alive loop to evade free-tier sleeping.
- Do not turn this service into a video proxy.

## Deployment as a normal Render Web Service

This service is not a Blueprint. Create a normal Render **Web Service** from the repository.

Option A — Root Directory empty:

- Build: `pip install -r requirements.txt`
- Start: `bash start.sh`
- Health: `/health`

Option B — Root Directory `backend`:

- Build: `pip install -r requirements.txt`
- Start: `bash start.sh`
- Health: `/health`

Both start commands run Uvicorn with `--no-access-log`. Set the environment variables from `.env.example` in Render. Full instructions: `docs/RENDER_BACKEND_SETUP.md`.

## Root-level local run

From the repository root:

```bash
pip install -r requirements.txt
bash start.sh
```
