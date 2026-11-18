"""Phase 3 media checks: placeholders replaced, media files used, within budget, with alt text."""
from pathlib import Path

import pytest

from conftest import WEBSITE
from sitecheck import html_pages, local_refs, soup

MEDIA = WEBSITE / "docs/assets/media"
MAX_ANIM_BYTES = 1_600_000
MAX_TOTAL_BYTES = 40_000_000
ANIM = (".gif", ".webp", ".mp4", ".webm")


def media_files() -> list[Path]:
    return sorted(f for f in MEDIA.rglob("*") if f.is_file() and f.name != ".gitkeep")


@pytest.mark.xfail(strict=True, reason="desktop app (GUI) screenshot deferred by owner; remove once gui.md and index.md have it")
def test_no_pending_media(site_dir):
    left = [(str(p.relative_to(site_dir)), f.get_text(strip=True)) for p in html_pages(site_dir)
            for f in soup(p).select(".oa-media--pending")]
    assert left == []


def test_media_within_budget():
    files = media_files()
    assert [f.name for f in files if f.suffix in ANIM and f.stat().st_size > MAX_ANIM_BYTES] == []
    assert sum(f.stat().st_size for f in files) <= MAX_TOTAL_BYTES


def test_every_media_file_is_used(site_dir):
    used = {Path(r).name for p in html_pages(site_dir) for r in local_refs(p) if "assets/media/" in r}
    assert sorted(f.name for f in media_files()) == sorted(used)


def test_media_images_have_alt_text(site_dir):
    missing = [(str(p.relative_to(site_dir)), img.get("src")) for p in html_pages(site_dir)
               for img in soup(p).select("img") if "assets/media/" in (img.get("src") or "")
               and not (img.get("alt") or "").strip()]
    assert missing == []
