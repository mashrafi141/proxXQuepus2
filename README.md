# ProxXQuepus

ProxXQuepus is a fast, premium native Android media browser and file manager built with Kotlin and Jetpack Compose. It discovers, organizes and plays media in hidden and custom folders without modifying original files, and now includes an optional TeraBox Cloud Mode.

## v1.0.2 highlights

- **Storage Mode selector** in Settings: Local Mode remains the default; Cloud Mode is opt-in and persisted.
- **TeraBox Cloud Mode** through the official TeraBox Open Platform OAuth flow:
  - Connect and disconnect accounts, add multiple accounts, switch accounts.
  - Choose a Cloud Root folder per account; root and browsing state are kept separate from Local Mode.
  - Folder browsing, pagination, search, sorting and type filters.
  - Cached cloud metadata with an offline banner and retry.
  - Cloud thumbnails with a separate bounded disk cache.
  - Photo viewer with pinch/pan, animated GIF support, and video playback with seek gestures and HLS fallback.
  - Per-account favourites, recently viewed and playback position.
  - Explicit user-initiated downloads to a SAF destination.
  - Rename/delete with confirmation where TeraBox permissions allow.
- **Lightweight FastAPI backend** for confidential OAuth token exchange/refresh only. It never proxies media bytes; video plays directly from TeraBox.
- Existing Local Mode remains intact: scanner, local database, file manager, thumbnail engine, player, security lock and UI are preserved.

## Local Mode

- Automatic/manual scanning of storage directories, including hidden and dot-prefixed folders, without modifying originals.
- Nested folder browsing, breadcrumbs, folder cards and custom local covers.
- Photos, videos, GIFs and audio with the existing gesture-rich player and zoom/pan viewer.
- Search, sorting, media-type filters, collections, favourites, liked media, recent history and multi-selection.
- PIN lock and auto-lock.
- Room metadata cache for instant folder loading.

Local Mode is the default after upgrading. Existing local database records, root folder selection, settings and media files are not touched by Cloud Mode.

## Cloud Mode requirements

Cloud Mode requires **official TeraBox Open Platform access**. The app does not ask for a TeraBox password, does not scrape the website, and does not use undocumented endpoints.

1. Apply to TeraBox for `client_id`, `client_secret` and `private_secret`; register the Android URL scheme (and optionally a backend HTTP callback).
2. Deploy `backend/` using the instructions in `docs/RENDER_BACKEND_SETUP.md`.
3. Build the app with the backend URL:

```bash
# Windows PowerShell
$env:TERABOX_BACKEND_BASE_URL = "https://your-service.onrender.com"
gradle :app:assembleRelease
```

Without credentials/approval, the app shows a precise setup/configuration state rather than a fake working cloud. For CI releases, set the repository variable `TERABOX_BACKEND_BASE_URL`; the workflow passes it to Gradle. See `docs/TERABOX_API_VALIDATION.md` for verified endpoints and blockers.

## Architecture & tech stack

- **Platform**: Android SDK 36, minSdk 24, Kotlin 2.2.
- **UI**: Jetpack Compose + Material 3, dark premium theme.
- **Local media**: Storage Access Framework, Room, Media3/ExoPlayer.
- **Cloud**: isolated `com.example.cloud` packages, separate Room database, Android Keystore token encryption, Coil thumbnail cache, official TeraBox Open API.
- **Backend**: FastAPI, httpx, Render Free-compatible. Auth/token coordination only.

See:

- `docs/CLOUD_MODE_ARCHITECTURE.md`
- `docs/TERABOX_API_VALIDATION.md`
- `docs/RENDER_BACKEND_SETUP.md`
- `docs/V1.0.2_CHANGELOG.md`

## Build

```bash
# The repository has no committed Gradle wrapper; use Gradle 9.3.1 as in CI.
gradle :app:assembleDebug
gradle :app:assembleRelease
gradle :app:lintDebug

cd backend
python -m compileall app
```

Release signing remains configured through the existing Gradle signing setup. Do not commit keystores, `.env` secrets or user tokens.

## Known limitations

- Live TeraBox OAuth and cloud file operations require official credentials and an authorized account; they were not live-tested in this workspace.
- TeraBox may restrict file APIs to an application-specific folder. Cloud Mode shows only what the official API returns.
- Cloud upload and move-to-folder are not exposed in v1.0.2.
- Direct dlink/HTTP Range behaviour needs measurement on an authorized account/network.
- Cloud folder cover customization and local-only obfuscation features do not apply to cloud files.