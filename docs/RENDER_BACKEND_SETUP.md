# Render Backend Setup — ProxXQuepus Cloud Mode (Normal Web Service)

This backend is deployed as a **normal Render Web Service**, not as a Blueprint. The repository is already structured so the service can be started from either the repository root or the `backend/` directory.

## Two supported Render setups

### Option A — Root Directory left empty (recommended)

- **Service type:** Web Service
- **Root Directory:** empty
- **Runtime:** Python 3
- **Build Command:** `pip install -r requirements.txt`
- **Start Command:** `bash start.sh`
- **Health Check Path:** `/health`

The root `requirements.txt` includes `backend/requirements.txt`, and `start.sh` runs:

```text
uvicorn backend.app.main:app --host 0.0.0.0 --port $PORT --no-access-log
```

### Option B — Root Directory set to `backend`

- **Service type:** Web Service
- **Root Directory:** `backend`
- **Runtime:** Python 3
- **Build Command:** `pip install -r requirements.txt`
- **Start Command:** `bash start.sh`
- **Health Check Path:** `/health`

The `backend/start.sh` runs:

```text
uvicorn app.main:app --host 0.0.0.0 --port $PORT --no-access-log
```

`backend/.python-version` and the root `.python-version` both request Python 3.12.8. If Render shows a different available version, set `PYTHON_VERSION` explicitly to that version.

## Environment variables

Set these in Render → your Web Service → Environment. Never put them in the Android APK or commit them.

| Variable | Required | Description |
|---|---|---|
| `TERABOX_CLIENT_ID` | Yes | TeraBox Open Platform AppKey |
| `TERABOX_CLIENT_SECRET` | Yes | TeraBox Open Platform SecretKey |
| `TERABOX_PRIVATE_SECRET` | Yes | TeraBox private secret used in the signature |
| `APP_REDIRECT_BASE` | Yes | `proxxquepus://terabox-oauth` |
| `STATE_SIGNING_SECRET` | Yes | Random 32+ character secret |
| `TERABOX_REDIRECT_URI` | Optional | Only for the optional backend HTTP callback mode |
| `ENABLE_API_DOCS` | No | Keep `false` in production |

Without the TeraBox credentials, the service still starts, but `/health` returns `configured: false` and `/v1/auth/start` returns `SERVER_NOT_CONFIGURED`. This is intentional.

## TeraBox developer registration

1. Apply for TeraBox Open Platform access.
2. Obtain `client_id`, `client_secret`, and `private_secret`.
3. Register the Android URL scheme:
   ```text
   proxxquepus://terabox-oauth
   ```
4. If you use the optional HTTP callback flow, also register:
   ```text
   https://<your-render-service>.onrender.com/v1/auth/callback
   ```

The documented Android flow returns the one-time authorization `code` directly to the app scheme. The app then calls:

```text
POST https://<your-render-service>.onrender.com/v1/auth/exchange
```

with the code and its pending signed state. The backend performs the confidential TeraBox token exchange. No tokens pass through the browser URL.

## Configure the Android app

At build time:

```powershell
$env:TERABOX_BACKEND_BASE_URL = "https://<your-render-service>.onrender.com"
gradle :app:assembleRelease
```

For GitHub Actions releases, set a repository variable:

```text
Settings → Secrets and variables → Actions → Variables
TERABOX_BACKEND_BASE_URL = https://<your-render-service>.onrender.com
```

The app rejects non-HTTPS backend URLs. The placeholder `https://YOUR-SERVICE.onrender.com` is treated as unconfigured.

## Verify

```bash
curl https://<your-render-service>.onrender.com/health
# {"status":"ok","service":"proxxquepus-cloud","version":"1.0.2","configured":true}

curl https://<your-render-service>.onrender.com/v1/auth/status
```

Then test with an authorized account:

1. Cloud Mode → Connect TeraBox.
2. Browser login → return via `proxxquepus://terabox-oauth?code=...`.
3. App exchanges the code through the backend.
4. Select a Cloud Root folder.
5. Verify folder browsing, thumbnails, photo/GIF viewing, video playback and seek.

## Render Free caveats

- Cold starts are expected.
- No persistent disk/database: optional HTTP-callback claims are memory-only.
- The Android code exchange (`/v1/auth/exchange`) is stateless on the backend and survives a restart within the state/code lifetime.
- No media/video bytes pass through Render. Playback goes directly to the authorized TeraBox URL.
- Do not add a keep-alive ping loop.
- Never log tokens, codes, signed media URLs, or OAuth query strings.

## Local run

```bash
cd backend
python -m venv .venv
. .venv/bin/activate          # Windows: .venv\Scripts\activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Or from the repository root:

```bash
pip install -r requirements.txt
bash start.sh
```
