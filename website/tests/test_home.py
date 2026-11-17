import re
from pathlib import Path

from sitecheck import soup

WEB = Path(__file__).resolve().parents[1]


def test_headline(site_dir):
    assert soup(site_dir / "index.html").select_one("h1").get_text(strip=True) == "The music bot you run yourself"


def test_path_cards_link_to_setup_and_migrating(site_dir):
    cards = [(a.get_text(" ", strip=True), a["href"].rstrip("/"))
             for a in soup(site_dir / "index.html").select("a.oa-path")]
    assert any(t.startswith("Set up a new bot") and h.endswith("setup") for t, h in cards)
    assert any(t.startswith("Coming from JMusicBot?") and h.endswith("migrating") for t, h in cards)


def test_replica_buttons_match_bot(site_dir):
    rows = [[b.get_text(" ", strip=True).split(" ", 1)[-1] for b in r.select(".oa-np__btn")]
            for r in soup(site_dir / "index.html").select(".oa-np__row")]
    assert rows == [["Prev", "Pause", "Skip", "Stop"], ["Shuffle", "Repeat", "Favorite", "Vol -", "Vol +"]]


def test_replica_is_labelled_image(site_dir):
    np = soup(site_dir / "index.html").select_one(".oa-np")
    assert np["role"] == "img" and "now-playing" in np["aria-label"].lower()


def test_now_playing_respects_reduced_motion():
    assert "prefers-reduced-motion" in (WEB / "docs/javascripts/now-playing.js").read_text(encoding="utf-8")
    assert "prefers-reduced-motion" in (WEB / "docs/stylesheets/home.css").read_text(encoding="utf-8")


def test_home_has_mobile_breakpoint():
    css = (WEB / "docs/stylesheets/home.css").read_text(encoding="utf-8")
    assert re.search(r"@media[^{]*max-width:\s*(7[0-9]{2}|8[0-9]{2}|9[0-9]{2})px", css)


def test_footer_credits_original(site_dir):
    f = soup(site_dir / "index.html").select_one("footer")
    assert f.select_one('a[href="https://github.com/jagrosh/MusicBot"]') and "Apache 2.0" in f.get_text()
