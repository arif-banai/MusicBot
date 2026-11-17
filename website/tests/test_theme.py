import re
from pathlib import Path

import pytest

from sitecheck import html_pages, soup

WEB = Path(__file__).resolve().parents[1]
CSS = WEB / "docs" / "stylesheets" / "on-air.css"

TOKENS = ["--oa-bg", "--oa-surface", "--oa-line", "--oa-text", "--oa-text-muted",
          "--oa-accent", "--oa-on-accent", "--oa-tip", "--oa-danger"]


def scheme_tokens(scheme: str) -> dict[str, str]:
    css = CSS.read_text(encoding="utf-8")
    block = re.search(r'\[data-md-color-scheme="%s"\]\s*\{([^}]*)\}' % re.escape(scheme), css)
    assert block, f"no token block for {scheme}"
    return {k: v.upper() for k, v in re.findall(r"(--oa-[a-z-]+)\s*:\s*(#[0-9A-Fa-f]{6})", block.group(1))}


def _luminance(hex_color: str) -> float:
    def channel(c: int) -> float:
        c = c / 255
        return c / 12.92 if c <= 0.03928 else ((c + 0.055) / 1.055) ** 2.4
    r, g, b = (int(hex_color[i:i + 2], 16) for i in (1, 3, 5))
    return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b)


def contrast(a: str, b: str) -> float:
    la, lb = sorted((_luminance(a), _luminance(b)), reverse=True)
    return (la + 0.05) / (lb + 0.05)


def test_dark_is_default_scheme(site_dir):
    assert soup(site_dir / "index.html").body["data-md-color-scheme"] == "slate"


def test_dark_tokens_match_spec():
    t = scheme_tokens("slate")
    assert (t["--oa-bg"], t["--oa-surface"], t["--oa-line"], t["--oa-text-muted"],
            t["--oa-text"], t["--oa-accent"], t["--oa-tip"]) == (
        "#171C33", "#222A4A", "#313A60", "#A9B1D0", "#F3F5FB", "#F5B83D", "#4CD6A0")


@pytest.mark.parametrize("scheme", ["slate", "default"])
def test_token_contrast(scheme):
    t = scheme_tokens(scheme)
    assert set(TOKENS) <= t.keys()
    assert contrast(t["--oa-text"], t["--oa-bg"]) >= 7.0
    assert contrast(t["--oa-text-muted"], t["--oa-bg"]) >= 4.5
    assert contrast(t["--oa-accent"], t["--oa-bg"]) >= 4.5
    assert contrast(t["--oa-on-accent"], t["--oa-accent"]) >= 4.5
    assert contrast(t["--oa-tip"], t["--oa-bg"]) >= 3.0


MAPPED = ["--md-typeset-a-color", "--md-accent-fg-color", "--md-primary-fg-color", "--md-default-bg-color",
          "--md-default-fg-color", "--md-code-bg-color", "--md-footer-bg-color"]


def _rules(css: str):
    css = re.sub(r"/\*.*?\*/", "", css, flags=re.S)
    for m in re.finditer(r"([^{}]+)\{([^{}]*)\}", css):
        yield m.group(1), m.group(2)


def _matches_body(selector: str, attrs: dict[str, str]) -> bool:
    """True if a plain attribute selector on <body> matches; anything else is skipped."""
    sel = selector.strip()
    negated = re.findall(r":not\(\[([\w-]+)=\"?([\w-]+)\"?\]\)", sel)
    sel = re.sub(r":not\([^)]*\)", "", sel)
    if not re.fullmatch(r"(\[[\w-]+(=\"?[\w-]+\"?)?\])+", sel):
        return False
    for name, value in re.findall(r"\[([\w-]+)(?:=\"?([\w-]+)\"?)?\]", sel):
        if name not in attrs or (value and attrs[name] != value):
            return False
    return all(attrs.get(n) != v for n, v in negated)


def _specificity(selector: str) -> int:
    return selector.count("[")


@pytest.mark.parametrize("scheme", ["slate", "default"])
def test_theme_mapping_outranks_palette(site_dir, scheme):
    body = soup(site_dir / "index.html").body
    attrs = {k: v for k, v in body.attrs.items() if k.startswith("data-md-color")}
    attrs["data-md-color-scheme"] = scheme

    def best(css_files, var):
        top = -1
        for f in css_files:
            for sels, decls in _rules(f.read_text(encoding="utf-8")):
                if re.search(re.escape(var) + r"\s*:", decls):
                    for s in sels.split(","):
                        if _matches_body(s, attrs):
                            top = max(top, _specificity(s))
        return top

    theme_css = list((site_dir / "assets" / "stylesheets").rglob("*.css"))
    ours = [site_dir / "stylesheets" / "on-air.css"]
    for var in MAPPED:
        assert best(ours, var) >= best(theme_css, var) >= -1, f"{var}: theme palette outranks on-air.css ({scheme})"


def test_fonts_loaded_every_page(site_dir):
    for p in html_pages(site_dir):
        hrefs = " ".join(l.get("href", "") for l in soup(p).select("link[rel=stylesheet]"))
        assert all(f in hrefs for f in ("Unbounded", "Onest", "JetBrains+Mono")), p


def test_theme_css_linked_every_page(site_dir):
    for p in html_pages(site_dir):
        assert any("stylesheets/on-air.css" in l.get("href", "") for l in soup(p).select("link")), p
