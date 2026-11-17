from sitecheck import html_pages, page_text, soup

EXPECTED = [
    "setup/index", "setup/java", "setup/bot-token", "setup/user-id", "setup/invite", "setup/first-run",
    "setup/docker", "setup/service", "setup/hosting", "features/slash-commands", "features/now-playing",
    "features/queue-history-playlists", "features/ytdlp-library", "features/dj-role", "features/sources",
    "features/gui", "features/lyrics", "reference/commands", "reference/config", "reference/proxy",
    "reference/performance", "reference/wont-add", "migrating", "help/troubleshooting", "help/faq",
]


def built(site_dir, slug):
    return site_dir / ("setup/index.html" if slug == "setup/index" else f"{slug}/index.html")


def test_every_outline_page_is_built(site_dir):
    assert [s for s in EXPECTED if not built(site_dir, s).is_file()] == []


def test_bot_token_page_is_real(site_dir):
    p = built(site_dir, "setup/bot-token")
    assert "Being written" not in page_text(p)
    assert len(soup(p).select(".oa-media--pending")) == 3
    tip = " ".join(a.get_text(" ") for a in soup(p).select(".admonition"))
    assert "Message Content Intent" in tip


def test_forbidden_copy(site_dir):
    for p in html_pages(site_dir):
        t = page_text(p).lower()
        assert "!play" not in t and "jmusicbot.com" not in t and "bypass" not in t, p
        assert "own risk" not in t and "may violate" not in t, p
