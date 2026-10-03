"""Serve a local HTML file to other devices on the local network."""

import argparse
import html as html_mod
import json
import os
import random
import re
import socket
import sys
import threading
import urllib.request
import webbrowser
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, quote, unquote, urlencode, urlparse


def lan_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except OSError:
        return socket.gethostbyname(socket.gethostname())
    finally:
        s.close()


def pick_file():
    try:
        import tkinter as tk
        from tkinter import filedialog
    except ImportError:
        sys.exit("usage: python lan_server.py <file.html> [--port N]")
    root = tk.Tk()
    root.withdraw()
    path = filedialog.askopenfilename(
        title="Select an HTML file",
        filetypes=[("HTML files", "*.html *.htm"), ("All files", "*.*")],
    )
    root.destroy()
    if not path:
        sys.exit("no file selected")
    return path


def default_html():
    """Find the bundled page: next to the exe/script first, then inside the bundle."""
    if getattr(sys, "frozen", False):
        exe_dir = os.path.dirname(sys.executable)
        bundle = getattr(sys, "_MEIPASS", exe_dir)
    else:
        exe_dir = bundle = os.path.dirname(os.path.abspath(__file__))
    for folder in (exe_dir, bundle):
        candidate = os.path.join(folder, "hentai roulette.html")
        if os.path.isfile(candidate):
            return candidate
    return None


BOORUS = [
    {"name": "xbooru", "api": "https://xbooru.com/index.php", "base": "https://xbooru.com"},
    {"name": "tbib", "api": "https://tbib.org/index.php", "base": "https://tbib.org"},
    {"name": "hypnohub", "api": "https://hypnohub.net/index.php", "base": "https://hypnohub.net"},
    {"name": "safebooru", "api": "https://safebooru.org/index.php", "base": "https://safebooru.org"},
]


SNOWFLAKE_FILTERS = {
    "propaganda": {"yaoi", "gay", "femboy", "twink", "homosexual", "boyslove",
                   "male_on_male", "solo_male", "male_focus", "2boys"},
    "sunset": {"dark_skin", "dark-skinned", "dark-skinned_female", "dark-skinned_male", "tanned"},
    "water": {"ai_generated", "stable_diffusion", "novelai", "ai_art"},
    "demons": {"lolicon", "shotacon", "loli", "shota", "underage", "teen", "young_girl"},
    "ballpit": {"furry", "anthro", "anthropomorphic", "beastman", "kemono", "furry_female"},
    "ack": {"futanari", "dickgirl", "dickgirl_on_female", "futanari_on_female",
            "futanari_on_male", "hermaphrodite", "intersex", "transgender", "shemale"},
}


RANDOM_PAGES = 10

ARTIST_LI_RE = re.compile(
    r'<li[^>]*class=["\'][^"\']*\btag-type-artist\b[^"\']*["\'][^>]*>(.*?)</li>',
    re.S | re.I,
)
ARTIST_ANCHOR_RE = re.compile(
    r'<a[^>]*href=["\']([^"\']*)["\'][^>]*>(.*?)</a>', re.S | re.I,
)
ALLOWED_ARTIST_HOSTS = {"xbooru.com", "tbib.org", "hypnohub.net", "safebooru.org"}
_artist_cache = {}


def extract_artist(page_html):
    """Pull artist tag names out of a booru post page's typed tag list."""
    names = []
    for block in ARTIST_LI_RE.findall(page_html):
        name = None
        for href, text in ARTIST_ANCHOR_RE.findall(block):
            text = html_mod.unescape(re.sub(r"<[^>]+>", "", text)).strip()
            if not text or text == "?":
                continue
            if "tags=" in href:
                name = text
                break
            if name is None:
                name = text
        if name and name not in names:
            names.append(name)
    return ", ".join(names) if names else None


def fetch_artist(source_url):
    """Fetch a post page (allowlisted hosts only) and return its artist tag."""
    if not source_url:
        return None
    if source_url in _artist_cache:
        return _artist_cache[source_url]
    parsed = urlparse(source_url)
    if parsed.scheme not in ("http", "https"):
        return None
    if (parsed.hostname or "").lower() not in ALLOWED_ARTIST_HOSTS:
        return None
    req = urllib.request.Request(source_url, headers={"User-Agent": "Mozilla/5.0"})
    with urllib.request.urlopen(req, timeout=10) as resp:
        page = resp.read().decode("utf-8", "replace")
    artist = extract_artist(page)
    if len(_artist_cache) >= 1000:
        _artist_cache.pop(next(iter(_artist_cache)))
    _artist_cache[source_url] = artist
    return artist


def fetch_posts(source, tags, pid=0):
    query = urlencode({
        "page": "dapi",
        "s": "post",
        "q": "index",
        "json": "1",
        "limit": "50",
        "pid": str(pid),
        "tags": tags,
    })
    req = urllib.request.Request(
        source["api"] + "?" + query,
        headers={"User-Agent": "Mozilla/5.0"},
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        body = resp.read().decode()
    if not body.strip():
        return []
    posts = json.loads(body)
    return posts or []


def post_image_url(source, post):
    url = post.get("file_url") or post.get("sample_url")
    if url:
        return url
    if post.get("directory") is not None and post.get("image"):
        return f'{source["base"]}/images/{post["directory"]}/{post["image"]}'
    return None


def post_page_url(source, post):
    if post.get("id") is None:
        return None
    return f'{source["base"]}/index.php?page=post&s=view&id={post["id"]}'


def filter_posts(posts, blacklist):
    if not blacklist:
        return posts
    return [p for p in posts if not (set(p.get("tags", "").split()) & blacklist)]


def random_booru_image(tags, blacklist=None):
    blacklist = blacklist or set()
    if tags in blacklist:
        return None
    for source in BOORUS:
        pid = random.randint(0, RANDOM_PAGES - 1)
        posts = []
        for attempt in ([pid, 0] if pid else [0]):
            try:
                posts = fetch_posts(source, tags, attempt)
            except Exception as exc:
                print(f'{source["name"]} failed for "{tags}" (pid {attempt}): {exc}',
                      file=sys.stderr)
                posts = []
            if posts:
                break
        if not posts:
            continue
        posts = filter_posts(posts, blacklist)
        if not posts:
            continue
        post = random.choice(posts)
        url = post_image_url(source, post)
        if url:
            print(f'image for "{tags}" from {source["name"]}', file=sys.stderr)
            return {
                "url": url,
                "source": post_page_url(source, post),
                "thumb": post.get("preview_url") or url,
            }
    return None


class Handler(SimpleHTTPRequestHandler):
    target = ""

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/api/image":
            qs = parse_qs(parsed.query)
            tags = qs.get("tags", [""])[0]
            blacklist = set()
            for name in qs.get("filters", [""])[0].split(","):
                name = name.strip()
                if name:
                    blacklist |= SNOWFLAKE_FILTERS.get(name, set())
            try:
                result = random_booru_image(tags, blacklist)
            except Exception as exc:
                print(f"api/image error: {exc}", file=sys.stderr)
                self.send_json(502, {"error": str(exc)})
                return
            if not result:
                self.send_json(404, {"error": "no results"})
                return
            self.send_json(200, result)
            return
        if parsed.path == "/api/artist":
            qs = parse_qs(parsed.query)
            source_url = qs.get("source", [""])[0]
            try:
                artist = fetch_artist(source_url)
            except Exception as exc:
                print(f"api/artist error: {exc}", file=sys.stderr)
                artist = None
            self.send_json(200, {"artist": artist})
            return
        if self.path in ("/", "/index.html"):
            self.send_response(302)
            self.send_header("Location", "/" + quote(self.target))
            self.end_headers()
            return
        super().do_GET()

    def send_json(self, code, payload):
        body = json.dumps(payload).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def end_headers(self):
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


def main():
    ap = argparse.ArgumentParser(description="Serve an HTML file on the local network")
    ap.add_argument("file", nargs="?",
                    help="path to the HTML file (bundled page used if omitted)")
    ap.add_argument("-p", "--port", type=int, default=8000)
    ap.add_argument("--browser", action="store_true",
                    help="serve without a window and open the default browser")
    ap.add_argument("--no-browser", action="store_true",
                    help="do not open the browser automatically")
    args = ap.parse_args()

    if args.file:
        path = os.path.abspath(args.file)
    else:
        path = default_html() or os.path.abspath(pick_file())
    if not os.path.isfile(path):
        sys.exit(f"error: file not found: {path}")

    Handler.target = os.path.basename(path)
    handler_cls = partial(Handler, directory=os.path.dirname(path))

    server = None
    for port in range(args.port, args.port + 20):
        try:
            server = ThreadingHTTPServer(("0.0.0.0", port), handler_cls)
            break
        except OSError:
            continue
    if server is None:
        sys.exit(f"error: no free port in {args.port}-{args.port + 19}")

    ip = lan_ip()
    local_url = f"http://127.0.0.1:{server.server_port}/"
    print(f"serving {path}")
    print(f"  on network:  http://{ip}:{server.server_port}/")
    print(f"  on this PC:  {local_url}")

    use_window = not args.browser
    webview_mod = None
    if use_window:
        try:
            import webview as webview_mod
        except ImportError as exc:
            print(f"pywebview unavailable ({exc}); using browser", file=sys.stderr)
            use_window = False

    try:
        if use_window:
            threading.Thread(target=server.serve_forever, daemon=True).start()
            try:
                webview_mod.create_window(
                    f"P20 Roulette - {ip}:{server.server_port}",
                    url=local_url,
                    width=1024,
                    height=860,
                    min_size=(520, 440),
                )
                storage = os.path.join(
                    os.environ.get("LOCALAPPDATA") or os.path.expanduser("~"),
                    "P20Roulette",
                )
                webview_mod.start(private_mode=False, storage_path=storage)
            except KeyboardInterrupt:
                print("\nstopped")
            except Exception as exc:
                print(f"window failed ({exc}); falling back to browser",
                      file=sys.stderr)
                if not args.no_browser:
                    webbrowser.open(local_url)
                try:
                    threading.Event().wait()
                except KeyboardInterrupt:
                    print("\nstopped")
        else:
            print("press Ctrl+C to stop")
            if not args.no_browser:
                threading.Timer(0.8, lambda: webbrowser.open(local_url)).start()
            try:
                server.serve_forever()
            except KeyboardInterrupt:
                print("\nstopped")
    finally:
        server.shutdown()
        server.server_close()


if __name__ == "__main__":
    main()
