"""Helpers for checking the built site in website/site."""
from functools import lru_cache
from pathlib import Path
from urllib.parse import unquote, urlsplit

from bs4 import BeautifulSoup

_EXTERNAL = ("http:", "https:", "mailto:", "data:", "//", "#")


def html_pages(site: Path) -> list[Path]:
    return sorted(p for p in site.rglob("*.html") if p.name != "404.html")


@lru_cache(maxsize=None)
def _soup(path: str) -> BeautifulSoup:
    return BeautifulSoup(Path(path).read_text(encoding="utf-8"), "html.parser")


def soup(path: Path) -> BeautifulSoup:
    return _soup(str(path))


def local_refs(page: Path) -> list[str]:
    refs = []
    for tag in soup(page).find_all(True):
        for attr in ("href", "src"):
            value = (tag.get(attr) or "").strip()
            if value and not value.startswith(_EXTERNAL):
                refs.append(value)
    return refs


def resolve_ref(page: Path, ref: str, site: Path) -> Path | None:
    path = unquote(urlsplit(ref).path)
    if not path:
        return page
    target = (page.parent / path).resolve()
    if target.is_dir():
        target = target / "index.html"
    if target.is_file() and target.is_relative_to(site.resolve()):
        return target
    return None


def page_text(page: Path) -> str:
    s = soup(page)
    node = s.select_one(".md-content") or s.select_one("main") or s
    return node.get_text(" ", strip=True)
