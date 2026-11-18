"""Files outside website/ that point people at the docs: no old jmusicbot.com links, and every site link resolves."""
import re
from pathlib import Path

WEB = Path(__file__).resolve().parents[1]
REPO = WEB.parent
SITE = "https://arif-banai.github.io/MusicBot/"
OUTSIDE = ["README.md", "Dockerfile", ".github/ISSUE_TEMPLATE/bug-report.yml",
           ".github/ISSUE_TEMPLATE/feature-request.yml", ".github/workflows/make-release.yml"]
OLD = re.compile(r"https?://(www\.)?jmusicbot\.com")
LINK = re.compile(r"https://arif-banai\.github\.io/MusicBot/([^\s)\"'>]*)")


def text(f):
    return (REPO / f).read_text(encoding="utf-8")


def test_cname_removed():
    assert not (REPO / "CNAME").exists()


def test_no_old_site_links():
    assert [f for f in OUTSIDE if OLD.search(text(f))] == []


def test_repo_site_links_resolve(site_dir):
    paths = {m.split("#")[0] for f in OUTSIDE for m in LINK.findall(text(f))}
    missing = [p for p in sorted(paths) if not (site_dir / p / "index.html").is_file() and not (site_dir / p).is_file()]
    assert missing == []


def test_readme_points_at_core_pages():
    r = text("README.md")
    for page in ["setup/", "setup/docker/", "reference/config/", "reference/commands/", "features/ytdlp-library/",
                 "reference/performance/", "reference/proxy/", "help/troubleshooting/", "migrating/"]:
        assert SITE + page in r, page


def test_readme_has_no_stale_text():
    r = text("README.md")
    for stale in ["0.6.2", "-All.jar", "generate a default", "bypass regional", "things-that-wont-be-added"]:
        assert stale not in r, stale
