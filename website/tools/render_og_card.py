"""Render docs/assets/images/og-card.png (1200x630) from the built home page.

Usage, from website/ with `zensical serve -a localhost:8765` running:
    python tools/render_og_card.py "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
Any Chromium-based browser works (Chrome, Edge, Chromium).
"""
import pathlib
import subprocess
import sys
import tempfile

WEB = pathlib.Path(__file__).resolve().parents[1]
BASE = "http://localhost:8765/MusicBot/"


def main(browser: str) -> None:
    page = (WEB / "site/index.html").read_text(encoding="utf-8")
    css = (WEB / "tools/og-card.css").read_text(encoding="utf-8")
    page = page.replace("<head>", f'<head><base href="{BASE}">', 1).replace("</head>", f"<style>{css}</style></head>", 1)
    out = WEB / "docs/assets/images/og-card.png"
    with tempfile.TemporaryDirectory() as tmp:
        html = pathlib.Path(tmp) / "og-card.html"
        html.write_text(page, encoding="utf-8")
        subprocess.run([browser, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--force-device-scale-factor=1",
                        "--window-size=1200,630", "--virtual-time-budget=4000", f"--screenshot={out}", html.as_uri()],
                       check=True, capture_output=True, timeout=120)
    print(out)


if __name__ == "__main__":
    main(sys.argv[1])
