# ProxXQuepus Cloud Mode Architecture (v1.0.2)

## Goals

- Preserve the existing Local Mode without behavioral or schema changes.
- Add a separate TeraBox Cloud Mode with equivalent practical features where the official API permits.
- Keep TeraBox developer secrets out of the APK.
- Stream video directly from TeraBox; never proxy media bytes through Render.
- Keep local and cloud state, navigation, caches and database records isolated.

## Mode selection

`StorageModeManager` stores `LOCAL` or `CLOUD` in DataStore:

- Default is `LOCAL`; existing installations upgrade without changing behavior.
- `MainActivity` observes the mode and composes either the existing local flow or `CloudModeApp`.
- The local `MainViewModel` is only obtained inside the local branch. Its scanner is not created while Cloud Mode is the persisted/active mode.
- Switching Local → Cloud calls `pauseScan()` on the local ViewModel before composing Cloud Mode.
- Switching Cloud → Local restarts the local background scan for the existing local root.

Local navigation/database state is never written by Cloud Mode. Cloud state uses separate DataStore keys, a separate Room database and a separate thumbnail cache.

## Android package layout

```text
com.example/
├── core/storage/                  StorageMode + DataStore preference
├── AppViewModel.kt                App-level storage mode state only
├── cloud/
│   ├── core/CloudModels.kt        Cloud domain models, errors, provider contract
│   ├── terabox/
│   │   ├── auth/                  Backend auth client + Keystore token store
│   │   ├── api/                   Official TeraBox Open API client
│   │   ├── database/              Separate Room database (version 1)
│   │   ├── repository/            Session refresh, cache, metadata, file ops
│   │   ├── settings/              Cloud DataStore preferences
│   │   ├── thumbnails/            Bounded Coil image/disk cache
│   │   └── ...
│   ├── viewmodel/CloudViewModel.kt
│   └── ui/                        Cloud-only screens and components
└── [existing local packages unchanged]
```

Existing local scanner, repository, file manager, thumbnail engine, player controller and screens remain in their original packages. The only local changes are:

- App version/code.
- The Settings storage-mode selector.
- A `pauseScan()` helper in `MainViewModel`.
- An optional MIME type argument on `VideoPlayerController.setSource` (defaults unchanged) so the cloud HLS fallback can be labelled correctly.
- MainActivity composition/deep-link handling.

## Authentication flow

The official Android integration opens the TeraBox login page in the system browser and TeraBox returns the one-time authorization `code` directly to the app's registered custom URL scheme. ProxXQuepus then performs the confidential exchange on the backend:

```text
Android                    Backend (Render)                  TeraBox
   |  GET /v1/auth/start          |                             |
   |----------------------------->| create signed state         |
   |  authorization URL + state   |                             |
   |<-----------------------------|                             |
   | store state; system browser: https://www.terabox.com/wap/outside/login |
   |------------------------------------------------------------>|
   |                          user authorizes                    |
   |<--- proxxquepus://terabox-oauth?code=... -------------------|
   | POST /v1/auth/exchange       |                             |
   |  {code, app-held state}      |                             |
   |----------------------------->| verify signed state         |
   |                              | POST /oauth/gettoken        |
   |                              |---------------------------->|
   |                              | POST /oauth/tokeninfo       |
   |                              |---------------------------->|
   |                              | GET /openapi/uinfo          |
   |                              |---------------------------->|
   | tokens + account (HTTPS)     |                             |
   |<-----------------------------|                             |
   | encrypt/store tokens         |                             |
```

The service also supports an optional web/HTTP-callback mode:

```text
TeraBox -> GET /v1/auth/callback?code=... -> one-time claim in memory
        -> 302 proxxquepus://terabox-oauth?status=ok&claim=...&state=...
App     -> POST /v1/auth/claim -> tokens/account
```

- `client_secret` and `private_secret` never leave the backend.
- OAuth state is HMAC-signed by the backend and stored in Cloud DataStore. The Android exchange must present the same pending state; random custom-scheme intents without it are rejected. The official Android return does not echo state in the documented example, so users should verify the connected account; the optional HTTP callback validates state server-side.
- Claim codes (optional HTTP callback) are random, one-time, memory-only, five-minute values.
- Tokens are encrypted with an Android Keystore AES-GCM key before being written to app-private storage.
- Refresh happens through the backend because TeraBox requires the confidential signature. Tokens rotate and the new values replace the old encrypted record.
- If the backend restarts during an optional claim window, the user restarts the connection; no token is persisted on Render.

## Data persistence

Cloud uses `proxxquepus_cloud_db` (Room, version 1) with:

- `cloud_accounts` — connected account metadata and API domain.
- `cloud_files` — metadata keyed by `(accountId, fsId)`, with parent path, type, size, modification time, optional video metadata and thumbnail URLs.
- `cloud_roots` — selected root folder per account, storing both `rootFsId` and API path.
- `cloud_media_state` — per-account favorite, last-viewed and playback position.
- `cloud_sync_state` — per-folder refresh timestamps.

The existing `proxxquepus_db` is not modified: same version, no new migration, no destructive migration introduced. Media bytes are never stored in Room.

The TeraBox list API navigates by path, while `fs_id` is the stable provider ID. ProxXQuepus stores both. If a folder path changes outside the app, browsing that saved path can fail; the app shows a clear error and lets the user choose a new root. It does not guess a new path.

## Browsing and caching

1. Cached folder rows are emitted immediately from Room.
2. The current folder is requested from TeraBox page 1 (`num=100`, `web=1`).
3. Metadata is written to Room; obsolete rows for that folder/page are reconciled.
4. A full refresh replaces page 1 and clears stale rows for that folder.
5. Additional pages are appended on demand.
6. Only the current folder is fetched; the app never recursively scans the whole account.
7. Requests are cancellable; navigating cancels the previous folder job.
8. Network failures keep cached metadata and show an offline banner. If no cache exists, the screen shows an error and retry.
9. Search uses the official account search endpoint and falls back to cached search results if the network is unavailable.
10. Thumbnails are loaded by Coil from official signed URLs with a dedicated bounded memory/disk cache under `cache/proxxquepus_cloud_thumbs`. Stable cache keys prevent re-download when the signed URL changes.

## Playback

- Videos resolve an official direct dlink through `/openapi/api/download` and pass the URL to Media3/ExoPlayer.
- If direct resolution fails, the app uses the official HLS streaming playlist, labelled with `MimeTypes.APPLICATION_M3U8`.
- The dlink includes the required `access_tokens` query parameter.
- The backend is not in the media data path; it only returns small JSON for auth/refresh.
- Expired dlink recovery: one automatic fresh-URL retry at the last position, then a manual retry action.
- Range/seek is handled by ExoPlayer and the TeraBox media host. Actual Range support is unverified without a live account.
- Photos are resolved from the same official download endpoint, decoded with sampling to keep memory bounded, then shown with the existing zoom/pan component.
- GIFs are decoded from downloaded bytes; API 28+ uses `AnimatedImageDrawable`.
- Local player code is unchanged apart from the optional MIME type parameter.

## File operations

- Rename/delete use the official `/openapi/api/filemanager` endpoint after explicit confirmation.
- Delete confirmation clearly states that remote TeraBox files will be deleted.
- Downloads are user-initiated and written only to a SAF destination chosen by the user.
- Disconnect and clear-cache never delete remote TeraBox files.
- Upload and move-to-folder are not exposed in v1.0.2.

## Backend on Render Free

The FastAPI service exposes only:

- `GET /health`
- `GET /v1/auth/status`
- `GET /v1/auth/start`
- `POST /v1/auth/exchange` (official Android code flow)
- `GET /v1/auth/callback` (optional HTTP callback)
- `POST /v1/auth/claim` (optional HTTP callback claim)
- `POST /v1/auth/refresh`

It uses environment variables, short timeouts, limited retries and a best-effort in-memory rate limiter. It does not write tokens to disk, does not proxy metadata, and never carries media bytes. Render Free sleeping/cold starts are expected; the app distinguishes network/backend failures from TeraBox authorization failures.
