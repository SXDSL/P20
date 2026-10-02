# P20 Roulette

Local LAN image roulette: a small Python server plus a single-page app that pulls
random anime images from booru APIs. Ships as a standalone Windows exe with its own
Chromium window.

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
- **Source disclosure** — footer link to the booru post page for the image
  currently displayed

## API

`GET /api/image?tags=<tag>[&filters=id1,id2]`

- `200` → `{"url": "<cdn image url>", "source": "<post page url>"}`
- `404` → `{"error": "no results"}` — no posts, or all excluded by filters
- `502` → upstream error

## Files

| File | Purpose |
|---|---|
| `hentai roulette.html` | Entire front end (page, tags, snowflake, retries) |
| `lan_server.py` | HTTP server: static files + `/api/image` proxy |
| `build.spec` | PyInstaller build (windowed, bundles page + icon) |
| `app.ico` | Exe icon (pink→purple gradient, P20) |
| `dist/P20-Roulette.exe` | Standalone app for distribution (~16 MB) |

## Build the exe

```
python -m pip install pyinstaller pywebview pillow
python -m PyInstaller build.spec --noconfirm
```

Output: `dist\P20-Roulette.exe`. To change the icon, replace `app.ico` and
rebuild. After editing `lan_server.py`, rebuild (or run from source) — HTML edits
need no rebuild.

## Notes

- Windows 10/11 x64, internet required for images
- LAN visitors must allow the Windows firewall prompt (private networks)
- SmartScreen may warn about the unsigned exe: More info → Run anyway
- If the embedded window can't open (missing WebView2/.NET), the app falls back
  to the default browser
