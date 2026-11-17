"""Content checks: pages are written, and the reference pages match the bot's code."""
import re
from pathlib import Path

import pytest

from sitecheck import html_pages, page_text, soup
from test_pages import built

REPO = Path(__file__).resolve().parents[2]
JAVA = REPO / "src/main/java/com/jagrosh/jmusicbot"
CATEGORIES = ("general", "music", "dj", "admin", "owner")


def assert_written(site_dir, slug):
    p = built(site_dir, slug)
    assert p.is_file(), slug
    text = page_text(p)
    assert "Being written" not in text, slug
    assert len(text.split()) >= 120, (slug, len(text.split()))


def _first_name(path: Path) -> str | None:
    text = path.read_text(encoding="utf-8")
    m = re.search(r'this\.name\s*=\s*"([^"]+)"', text)
    if m:
        return m.group(1)
    # Commands named through a constructor, e.g. NowPlayingSlashCmd -> this(bot, "nowplaying", ...)
    m = re.search(r'(?:this|super)\(\s*bot\s*,\s*"([^"]+)"', text)
    return m.group(1) if m else None


def prefix_commands() -> dict[str, str]:
    out = {}
    for cat in CATEGORIES:
        for f in sorted((JAVA / "commands/v1" / cat).glob("*Cmd.java")):
            name = _first_name(f)
            if name:
                out[name] = cat
    return out


SLASH_SUBCOMMANDS = {"LibraryPanelSlashCmd.java": "library panel", "LibraryUnlinkSlashCmd.java": "library unlink"}


def slash_commands() -> dict[str, str]:
    out = {}
    for cat in CATEGORIES:
        for f in sorted((JAVA / "commands/v2" / cat).glob("*SlashCmd.java")):
            name = SLASH_SUBCOMMANDS.get(f.name) or _first_name(f)
            if name:
                out[name] = cat
    return out


def config_keys() -> list[str]:
    stack, keys = [], []
    for line in (REPO / "src/main/resources/reference.conf").read_text(encoding="utf-8").splitlines():
        s = re.sub(r"#.*", "", line).strip()
        if not s:
            continue
        m = re.match(r"^([A-Za-z_][\w-]*)\s*\{\s*$", s)
        if m:
            stack.append(m.group(1))
            continue
        if s == "}":
            stack.pop()
            continue
        m = re.match(r"^([A-Za-z_][\w-]*)\s*[=:]", s)
        if m:
            keys.append(".".join(stack + [m.group(1)]))
    return [k for k in keys if not k.startswith(("meta.", "commands.aliases."))]


# ---------- Task 1: reference pages ----------

def test_config_key_parser_finds_known_keys():
    keys = config_keys()
    assert {"discord.token", "nowPlaying.showProgressBar", "ytdlp.matching.alignSwitch"} <= set(keys)
    assert not any(k.startswith(("meta.", "commands.aliases.")) for k in keys)


def test_commands_page_covers_every_command(site_dir):
    s = soup(built(site_dir, "reference/commands"))
    prefix_cells = {td.get_text(strip=True) for td in s.select(".md-content tr > td:nth-child(1)")}
    slash_cells = {td.get_text(strip=True) for td in s.select(".md-content tr > td:nth-child(2)")}
    missing = [n for n in prefix_commands() if n not in prefix_cells] + \
              [f"/{n}" for n in slash_commands() if f"/{n}" not in slash_cells]
    assert missing == []


def test_commands_grouped_by_permission(site_dir):
    heads = [h.get_text(strip=True).rstrip("¶") for h in soup(built(site_dir, "reference/commands")).select(".md-content h2")]
    assert heads[:5] == ["General", "Music", "DJ", "Admin", "Owner"]


def test_config_page_covers_every_key(site_dir):
    text = page_text(built(site_dir, "reference/config"))
    assert [k for k in config_keys() if k not in text] == []


@pytest.mark.parametrize("slug", ["reference/commands", "reference/config", "reference/proxy",
                                  "reference/performance", "reference/wont-add"])
def test_reference_pages_written(site_dir, slug):
    assert_written(site_dir, slug)


# ---------- Task 2: setup pages ----------

PERMISSION_NAMES = {
    "VIEW_CHANNEL": "View Channels", "MESSAGE_SEND": "Send Messages", "MESSAGE_HISTORY": "Read Message History",
    "MESSAGE_ADD_REACTION": "Add Reactions", "MESSAGE_EMBED_LINKS": "Embed Links",
    "MESSAGE_ATTACH_FILES": "Attach Files", "MESSAGE_MANAGE": "Manage Messages",
    "MESSAGE_EXT_EMOJI": "Use External Emojis", "VOICE_CONNECT": "Connect", "VOICE_SPEAK": "Speak",
    "NICKNAME_CHANGE": "Change Nickname",
}


def recommended_permissions() -> list[str]:
    text = (JAVA / "JMusicBot.java").read_text(encoding="utf-8")
    block = re.search(r"RECOMMENDED_PERMS\s*=\s*\{([^}]*)\}", text).group(1)
    return re.findall(r"Permission\.(\w+)", block)


SETUP = ["setup/index", "setup/java", "setup/user-id", "setup/invite", "setup/first-run",
         "setup/docker", "setup/service", "setup/hosting"]


@pytest.mark.parametrize("slug", SETUP)
def test_setup_pages_written(site_dir, slug):
    assert_written(site_dir, slug)


def test_java_page_states_requirements(site_dir):
    t = page_text(built(site_dir, "setup/java"))
    assert "Java 25" in t and "2.38" in t and "java -version" in t


def test_invite_page_lists_scopes_and_permissions(site_dir):
    t = page_text(built(site_dir, "setup/invite"))
    assert "applications.commands" in t and "bot" in t
    assert set(recommended_permissions()) <= PERMISSION_NAMES.keys()
    assert [p for p in recommended_permissions() if PERMISSION_NAMES[p] not in t] == []


def test_docker_page_uses_fork_image(site_dir):
    t = page_text(built(site_dir, "setup/docker"))
    assert "ghcr.io/arif-banai/musicbot" in t and "jagrosh" not in t.lower()


def test_no_stale_jar_versions(site_dir):
    for p in html_pages(site_dir):
        assert "0.6.2" not in page_text(p), p


# ---------- Task 3: feature and help pages ----------

FEATURES = ["features/slash-commands", "features/now-playing", "features/queue-history-playlists",
            "features/ytdlp-library", "features/dj-role", "features/sources", "features/gui"]


@pytest.mark.parametrize("slug", FEATURES + ["help/troubleshooting", "help/faq"])
def test_feature_and_help_pages_written(site_dir, slug):
    assert_written(site_dir, slug)


@pytest.mark.parametrize("slug", FEATURES)
def test_feature_pages_have_a_media_slot(site_dir, slug):
    assert soup(built(site_dir, slug)).select(".oa-media")


def test_ytdlp_page_documents_its_keys(site_dir):
    t = page_text(built(site_dir, "features/ytdlp-library"))
    assert [k for k in config_keys() if k.startswith("ytdlp.") and k.split(".")[-1] not in t] == []


def test_youtube_settings_are_symptom_only(site_dir):
    for slug in ["help/troubleshooting", "features/ytdlp-library", "reference/config", "reference/proxy"]:
        t = page_text(built(site_dir, slug)).lower()
        for banned in ("regional restriction", "get around", "circumvent", "evade", "detection"):
            assert banned not in t, (slug, banned)


def test_troubleshooting_covers_required_symptoms(site_dir):
    t = page_text(built(site_dir, "help/troubleshooting"))
    for needle in ("Your bot is missing required Discord intents!", "2.38", "UnsupportedClassVersionError",
                   "file.encoding=UTF-8", "useOAuth", "applications.commands"):
        assert needle in t, needle


def test_lyrics_page_removed(site_dir):
    assert not (site_dir / "features/lyrics/index.html").exists()


# ---------- Task 4: migration page ----------

def test_migrating_page_covers_upgrade_steps(site_dir):
    assert_written(site_dir, "migrating")
    t = page_text(built(site_dir, "migrating"))
    for needle in ("Java 25", "2.38", "Message Content Intent", "config.txt", "/play"):
        assert needle in t, needle
    assert soup(built(site_dir, "migrating")).select_one('.md-content a[href="https://github.com/jagrosh/MusicBot"]')
