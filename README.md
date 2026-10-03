# P20 Roulette

Local LAN image roulette: a small Python server plus a single-page app that pulls
random anime images from booru APIs. Ships as a standalone Windows exe with its own
Chromium window, and as a standalone Android APK.

**NSFW** — adult anime content.

## Run

**As an app:**

- Double-click `dist\P20-Roulette.exe`
- Opens its own window; the title bar shows `P20 Roulette - <lan-ip>:<port>` —
  share that URL with anyone on the same network
- Closing the window stops the server

**From source:**

```
python lan_server.py              # windowed mode (needs pywebview)
python lan_server.py --browser    # console + browser, prints URLs
python lan_server.py [file.html] [-p PORT] [--no-browser]
```

With no file argument it serves `hentai roulette.html` from the script's folder
(or the copy bundled inside the exe). Uses the first free port from 8000 upward.

## Features

- **Sources** — xbooru → tbib → hypnohub → safebooru fallback chain, random result
  pages, last 150 shown URLs remembered per session to avoid repeats, auto-retry
  (5 attempts) with broken images hidden
- **Tags** — 30 staples + 10 weekly tags (deterministic per week), chosen in the
  Tags overlay, saved in localStorage
- **Snowflake** — 6 blacklist filter toggles (all off by default), saved
  separately, passed to the server as `filters`
- **Artist & source** — the artist tag and a link to the booru post page shown
  next to the category chip under the image
- **Favorites** — a star in the image's bottom-right corner saves it (gold,
  animated) to localStorage; the Favorites button opens a scrollable thumbnail
  grid with a back button, per-tile remove (×), and tap-to-reload

## API

`GET /api/image?tags=<tag>[&filters=id1,id2]`

- `200` → `{"url", "source", "thumb"}` — full image, post page, preview thumbnail
- `404` → `{"error": "no results"}` — no posts, or all excluded by filters
- `502` → upstream error

`GET /api/artist?source=<post page url>`

- `200` → `{"artist": "<tag>" | null}` — scraped from the post page's typed tag
  list; host-allowlisted to the four boorus, results cached (null on failure)

## Files

| File | Purpose |
|---|---|
| `hentai roulette.html` | Entire front end (page, tags, snowflake, retries) |
| `lan_server.py` | HTTP server: static files + `/api/image` + `/api/artist` |
| `build.spec` | PyInstaller build (windowed, bundles page + icon) |
| `app.ico` | Exe icon (pink→purple gradient, P20) |
| `dist/P20-Roulette.exe` | Standalone Windows app (~16 MB) |
| `android/` | Android project (Kotlin WebView app + JVM unit tests) |
| `dist/P20-Roulette.apk` | Standalone Android build (~0.8 MB) |

## Build the exe

```
python -m pip install pyinstaller pywebview pillow
python -m PyInstaller build.spec --noconfirm
```

Output: `dist\P20-Roulette.exe`. To change the icon, replace `app.ico` and
rebuild. After editing `lan_server.py`, rebuild (or run from source) — HTML edits
need no rebuild.

## Android app

`dist/P20-Roulette.apk` (Android 7+) — the same page in a native WebView with the
image API ported to Kotlin; runs entirely on the phone, no PC required. Install by
copying the APK to a device and opening it (allow "unknown sources"). Debug-signed,
which is fine for personal installs.

Rebuild after source changes:

```
set "JAVA_HOME=C:\Program Files\Java\jdk-17"  rem (path to your JDK 17 install)
cd android
gradlew.bat test assembleDebug
```

Output: `android\app\build\outputs\apk\debug\app-debug.apk`. After editing
`hentai roulette.html`, copy it to `android\app\src\main\assets\index.html`
(unit tests fail if the copies differ).

## Notes

- Windows 10/11 x64, internet required for images
- LAN visitors must allow the Windows firewall prompt (private networks)
- SmartScreen may warn about the unsigned exe: More info → Run anyway
- If the embedded window can't open (missing WebView2/.NET), the app falls back
  to the default browser
