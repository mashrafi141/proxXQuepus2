# TeraBox API Validation — ProxXQuepus v1.0.2

Validated against the official **TeraBox Open Platform Integration Document** on 2026-11-15:

- Public document route: `https://www.terabox.com/integrations/docs`
- The route is a client-rendered SPA. Its published documentation bundle (`index.d29a8c1b.js`) and main bundle were inspected to retrieve the official endpoint paths, parameter names, examples and error codes. No undocumented endpoint was used.
- Official examples in this document are reproduced only where needed to define the implementation; no credential or personal account value is real.

## Authentication prerequisites

1. Apply to TeraBox for an Open Platform application.
2. Receive `client_id` (AppKey), `client_secret` (SecretKey) and `private_secret`.
3. Register the Android URL scheme with TeraBox for the authorization-code flow. Optionally register a backend HTTPS callback if you use the Web/HTTP callback mode.
4. The Android flow uses the official TeraBox web authorization page invoked in the system browser:

```text
https://www.terabox.com/wap/outside/login?clientId=<client_id>&isFromApp=1
```

Official documentation references an app URL scheme in the form `<app name>://teraboxOauth`. ProxXQuepus uses:

```text
proxxquepus://terabox-oauth
```

Official Android example code confirms the TeraBox login page returns the one-time authorization `code` directly to this custom scheme (for example `proxxquepus://terabox-oauth?code=...`). ProxXQuepus forwards the code and its app-held signed state to the backend for the confidential exchange. Tokens are not placed in the callback URL.

Security caveat: the official Android document does not show a `state` value being echoed by TeraBox in the custom-scheme return. Custom URL schemes can be invoked by other apps. ProxXQuepus mitigates this by requiring a pending, HMAC-signed state held only in app-private DataStore; the backend rejects an exchange without it. A malicious intent delivered while a real connection is pending cannot be fully eliminated by the app without the provider echoing state. Users should verify the connected account shown after authorization. The optional backend HTTP callback mode validates state server-side.

The backend also supports an optional HTTP web-callback mode that redirects to the deep link with a short-lived, one-time claim code. This keeps tokens out of browser history and URL logs in both modes.

## Verified official endpoints

### Authorization service

| Capability | Method and path | Key parameters | Notes |
|---|---|---|---|
| Exchange code for tokens | `POST https://www.terabox.com/oauth/gettoken` | `client_id`, `client_secret`, `grant_type=authorization_code`, `code`, `timestamp`, `sign` | Form body. Official signature: `md5("client_id_timestamp_client_secret_private_secret")`. |
| Token information | `POST https://www.terabox.com/oauth/tokeninfo` | `access_token` | Returns `api_domain`, `upload_domain`, `user_id`, `expires_in`, `create_time`. |
| Refresh tokens | `POST https://www.terabox.com/oauth/refreshtoken` | `client_id`, `client_secret`, `refresh_token`, `timestamp`, `sign` | Refresh tokens are single-use and rotate. |
| Device code (not used) | `GET https://www.terabox.com/oauth/devicecode` | `client_id` | Official alternative QR flow; not used by ProxXQuepus. |

### ProxXQuepus backend coordination endpoints

These are not TeraBox endpoints; they keep confidential credentials off the device.

| Endpoint | Purpose |
|---|---|
| `GET /v1/auth/start` | Creates signed state and returns the official TeraBox login URL. |
| `POST /v1/auth/exchange` | Accepts the Android `code` + pending state; performs the official `/oauth/gettoken`, token-info and account-info calls; returns encrypted-at-transport tokens over HTTPS. |
| `GET /v1/auth/callback` | Optional HTTP callback mode; stores a memory-only one-time claim. |
| `POST /v1/auth/claim` | Optional HTTP callback mode; consumes the claim once. |
| `POST /v1/auth/refresh` | Official signed refresh with the single-use refresh token. |

Official token lifetime notes:

- Access token: 2 days.
- Refresh token: 30 days.
- Authorization code: 5 minutes, single use.
- After a successful refresh, the previous access token expires within 15 seconds; the returned refresh token must replace the old one.

### Open API service

The official document says to use the `api_domain` returned by `/oauth/tokeninfo` for basic services, and the upload domain for `/rest/2.0/pcs/superfile2`.

| Capability | Method and path | Key parameters | Implementation status |
|---|---|---|---|
| Account profile | `GET /openapi/uinfo` | `access_tokens` | Implemented |
| Quota | `GET /openapi/api/quota` | `access_tokens` | Documented, not displayed |
| Folder listing | `GET /openapi/api/list` | `access_tokens`, `dir`, `num`, `page`, `order`, `desc`, `web=1` | Implemented |
| File metadata | `GET /openapi/api/filemetas` | `access_tokens`, `target`, `dlink` | Documented |
| Search | `GET /openapi/api/search` | `access_tokens`, `key`, `page`, `num`, `order`, `desc`, `recursion` | Implemented |
| Download link | `GET /openapi/api/download` | `access_tokens`, `fidlist`, `type=dlink` | Implemented |
| Online playback | `GET /openapi/api/streaming` | `access_tokens`, `path`, `type=M3U8_AUTO_480/720/1080` | Implemented fallback |
| File manager | `POST /openapi/api/filemanager` | `access_tokens`, `opera`, `async`, body `filelist` | Rename/delete implemented; move/copy documented but not exposed |
| Pre-upload | `POST /openapi/api/precreate` | `autoinit=1`, `block_list`, `path` | Documented; upload not exposed in v1.0.2 |
| Shard upload | `POST /rest/2.0/pcs/superfile2` on `upload_domain` | `method=upload`, `app_id=250528`, `path`, `uploadid`, `partseq`, file bytes | Documented; upload not exposed in v1.0.2 |
| File creation | `POST /openapi/api/create` | `path`, `size`, `uploadid`, `block_list`, rename policy | Documented; upload not exposed in v1.0.2 |

### File listing response fields used by the app

The official listing example contains:

`category`, `fs_id`, `md5`, `local_ctime`, `local_mtime`, `server_ctime`, `server_mtime`, `server_filename`, `size`, `isdir`, `path`, `thumbs.url1`, `thumbs.url2`, `thumbs.url3`, `thumbs.icon`.

Category mapping used by the app:

1. video
2. audio
3. image (GIF detected by filename extension)
4. document
5. application
6. other
7. seed

### Download and playback URLs

Official `download` example:

```text
GET https://www.terabox.com/openapi/api/download?access_tokens=<token>&fidlist=[<fs_id>]&type=dlink
```

The response contains `dlink[0].dlink`. The official documentation states that the `access_tokens` request parameter must be concatenated to the returned dlink when downloading.

ProxXQuepus resolves this direct dlink first for video playback. If direct dlink resolution is rejected, it falls back to the official streaming playlist:

```text
GET https://www.terabox.com/openapi/api/streaming?access_tokens=<token>&path=<path>&type=M3U8_AUTO_720
```

The playlist contains direct segment URLs; video bytes do **not** pass through the Render backend.

## Important limitations and caveats

### Application-folder restriction

The official document states that the file path for basic file APIs is fixed under:

```text
/From: Other Applications/Application Name-{client_id}/
```

This may mean an app can only list and operate inside its approved application folder, not the entire personal TeraBox account. ProxXQuepus does not attempt to bypass this. The root-folder picker shows what the official API actually returns. If TeraBox grants only the application folder, users can select a root inside that folder.

### Live validation blocker

No TeraBox Open Platform developer credentials, approval or authorized test account were available in the workspace. Therefore:

- OAuth, token exchange and refresh are implemented against the official request/response shapes but have **not** been executed against a live account.
- Folder listing, search, thumbnail URLs, dlink playback, HTTP Range behaviour, rename and delete remain unverified live.
- The app and backend must not be described as successfully connected to TeraBox until the user completes official developer registration and runs the acceptance tests.

### HTTP Range / seeking

The official documentation does not explicitly guarantee HTTP Range support for `d.terabox.com` dlinks. The implementation uses the direct dlink with Media3/ExoPlayer, which requests ranges when the server advertises/supports them. This must be measured on real authorized videos. The backend never proxies media bytes.

### Thumbnail expiry

Official listing examples show signed thumbnail URLs (the sample includes `expires=8h`). ProxXQuepus stores the URL but caches thumbnails using a stable key based on account, file ID and modification time, so a cached thumbnail remains usable after metadata refresh. Expired thumbnail URLs are replaced on the next folder refresh.

### API rate limits

The official auth error code list includes `300001` (requests too frequent). Numeric rate limits are not published. The server and client implement conservative retries/backoff and report rate-limit errors without aggressive polling. Device-code polling is not used.

### Not exposed in v1.0.2

- Upload: the official three-step upload API exists, but multipart upload, permissions and resumability cannot be validated without credentials. No upload UI is shown.
- Move/copy: supported by the official file manager API but not exposed in this release.
- Folder cover customization: the local-only custom cover system is not extended to cloud folders.
- Multiple accounts: supported locally with one active account at a time, each with its own encrypted token and cloud root. TeraBox API permissions determine whether multiple accounts can be authorized.

## Exact user action required

To enable live Cloud Mode:

1. Apply for TeraBox Open Platform access and obtain `client_id`, `client_secret` and `private_secret`.
2. Register the Android URL scheme (and optionally the backend callback URL) with TeraBox.
3. Deploy `backend/` to Render (or another HTTPS host) and set the environment variables from `backend/.env.example`.
4. Rebuild the Android app with `TERABOX_BACKEND_BASE_URL=https://<your-backend>` and reinstall.
5. Connect an account you are authorized to use, then run acceptance checks on your device with an authorized account.

No credentials or tokens are included in this repository or documentation.
