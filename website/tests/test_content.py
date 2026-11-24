"""Content checks: pages are written, and the reference pages match the bot's code."""
import re
from pathlib import Path

import pytest

from sitecheck import html_pages, page_text, soup
from test_pages import built

WEB = Path(__file__).resolve().parents[1]

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
    prefix, slash = registered_commands()
    missing = [n for n in prefix if n not in prefix_cells] + [f"/{n}" for n in slash if f"/{n}" not in slash_cells]
    assert missing == []


def test_commands_grouped_by_permission(site_dir):
    heads = [h.get_text(strip=True).rstrip("¶") for h in soup(built(site_dir, "reference/commands")).select(".md-content h2")]
    assert heads[:5] == ["General", "Music", "DJ", "Admin", "Owner"]


def test_config_page_covers_every_key(site_dir):
    text = page_text(built(site_dir, "reference/config"))
    assert [k for k in config_keys() if k not in text] == []


@pytest.mark.parametrize("slug", ["reference/commands", "reference/config", "reference/proxy",
                                  "reference/performance"])
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


# The desktop app page has no media until its deferred screenshot exists (phase 4 plan, Task 1).
@pytest.mark.parametrize("slug", [f for f in FEATURES if f != "features/gui"])
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


# ---------- Task 5: no stubs left ----------

def test_no_stub_pages_remain(site_dir):
    assert [str(p.relative_to(site_dir)) for p in html_pages(site_dir) if "Being written" in page_text(p)] == []


# ---------- Review fixes (phase 2 final review) ----------

LEVELS = {"OwnerCommand": "Owner", "AdminCommand": "Admin", "DJCommand": "DJ", "MusicCommand": "Music",
          "OwnerSlashCommand": "Owner", "AdminSlashCommand": "Admin", "DJSlashCommand": "DJ",
          "MusicSlashCommand": "Music"}


def _class_files() -> dict[str, Path]:
    return {f.stem: f for f in (JAVA / "commands").rglob("*.java")}


def _level(cls: str, files: dict[str, Path]) -> str:
    """Permission level from the class a command extends, following subclasses (e.g. NpSlashCmd)."""
    while cls in files:
        m = re.search(r"class\s+\w+\s+extends\s+(\w+)", files[cls].read_text(encoding="utf-8"))
        if not m:
            break
        cls = m.group(1)
        if cls in LEVELS:
            return LEVELS[cls]
    return "General"


def registered_commands() -> tuple[dict[str, str], dict[str, str]]:
    """(prefix name -> level, slash name -> level) for the commands CommandFactory actually registers."""
    files = _class_files()
    src = (JAVA / "commands/v1/CommandFactory.java").read_text(encoding="utf-8")
    src = "\n".join(l for l in src.splitlines() if not l.strip().startswith("//"))
    prefix, slash = {"about": "General", "ping": "General", "help": "General"}, {}
    for cls in dict.fromkeys(re.findall(r"new\s+(\w+Cmd)\(", src)):
        name = _first_name(files[cls])
        level = _level(cls, files)
        (slash if cls.endswith("SlashCmd") else prefix)[name] = level
    if "library" in slash:
        # /library is a parent command; its subcommands are DJ-only, so it is listed under DJ.
        slash.update({"library": "DJ", "library panel": "DJ", "library unlink": "DJ"})
    return prefix, slash


def _rows_by_group(site_dir):
    """{(column index, cell text): h2 group} for the commands page tables."""
    out, group = {}, None
    for el in soup(built(site_dir, "reference/commands")).select_one(".md-content article, .md-content").find_all(["h2", "tr"]):
        if el.name == "h2":
            group = el.get_text(strip=True).rstrip("¶")
            continue
        for i, td in enumerate(el.find_all("td")[:2]):
            out[(i, td.get_text(strip=True))] = group
    return out


def test_registered_commands_listed_under_their_level(site_dir):
    rows = _rows_by_group(site_dir)
    prefix, slash = registered_commands()
    wrong = [(n, lvl, rows.get((0, n))) for n, lvl in prefix.items() if rows.get((0, n)) != lvl] + \
            [(f"/{n}", lvl, rows.get((1, f"/{n}"))) for n, lvl in slash.items() if rows.get((1, f"/{n}")) != lvl]
    assert wrong == []


def test_setup_order_is_consistent(site_dir):
    order = ["setup/java", "setup/bot-token", "setup/user-id", "setup/first-run", "setup/invite"]
    for here, nxt in zip(order, order[1:]):
        links = [a["href"] for p in soup(built(site_dir, here)).select(".md-content p")
                 if p.get_text(strip=True).startswith("Next:") for a in p.select("a")]
        assert links and links[0].rstrip("/").endswith(nxt.split("/")[1]), (here, links)
    nav = [a.get_text(strip=True) for a in soup(built(site_dir, "setup/index")).select(".md-nav--primary .md-nav__link")]
    titles = ["Install Java 25", "Get a bot token", "Find your user ID", "First run", "Invite the bot"]
    assert [t for t in nav if t in titles][:5] == titles


def test_first_run_describes_empty_answers(site_dir):
    t = page_text(built(site_dir, "setup/first-run"))
    assert "Invalid User ID! Exiting." in t and "doesn't create the file" not in t


def test_docker_moving_covers_folder_owner(site_dir):
    s = soup(built(site_dir, "setup/docker"))
    h = next(h for h in s.select(".md-content h2") if "Moving from the jar" in h.get_text())
    section = " ".join(sib.get_text(" ") for sib in h.find_next_siblings() if sib.name != "h2")
    assert "10001" in section or "--user" in section


def test_docker_latest_tag_is_described_correctly(site_dir):
    # docker-publish.yml: release tags set `latest`; master builds go to `master` and never to `latest`.
    t = page_text(built(site_dir, "setup/docker"))
    assert re.search(r"latest\s*:\s*the newest release", t), t
    assert re.search(r"master\s*:\s*the newest build of the\s*master\s*branch", t), t


def test_playlist_write_permissions(site_dir):
    t = page_text(built(site_dir, "features/queue-history-playlists"))
    assert "Creating and deleting playlist files: the bot owner" not in t
    assert "DJs and the bot owner" in t


def test_seek_permission_documented(site_dir):
    rows = {tr.find("td").get_text(strip=True): tr.get_text(" ") for tr in
            soup(built(site_dir, "reference/commands")).select(".md-content tr") if tr.find("td")}
    assert "DJ" in rows["seek"]


def test_slash_clear_options_not_none(site_dir):
    assert "Use `NONE` with either" not in page_text(built(site_dir, "features/dj-role"))
    assert "Use NONE with either" not in page_text(built(site_dir, "features/dj-role"))


def test_slash_troubleshooting_mentions_hash_file(site_dir):
    t = page_text(built(site_dir, "help/troubleshooting"))
    assert ".slashcommands.hash" in t


def test_no_false_eval_or_lyrics_claims(site_dir):
    for slug in ["reference/config", "reference/commands", "migrating"]:
        t = page_text(built(site_dir, slug))
        assert "doesn't include Nashorn" not in t and "doesn't work out of the box" not in t, slug
    t = page_text(built(site_dir, "migrating"))
    assert "is still listed" not in t and "Skipping." not in t


def release_version():
    # pom.xml holds x.y.z-SNAPSHOT while a release is prepared and x.y.z once Make Release tags it; the docs
    # name the jar of that release either way.
    pom = (WEB.parent / "pom.xml").read_text(encoding="utf-8")
    version = re.search(r"<artifactId>JMusicBot</artifactId>\s*<version>([^<]+)</version>", pom).group(1)
    return version.removesuffix("-SNAPSHOT")


def test_jar_name_matches_release_asset():
    bad = [(p.name, l) for p in (WEB / "docs").rglob("*.md") for l in p.read_text(encoding="utf-8").splitlines()
           if "-All.jar" in l]
    assert bad == []
    jar = f"JMusicBot-{release_version()}.jar"
    assert jar in (WEB / "docs/setup/first-run.md").read_text(encoding="utf-8")
    stale = [(p.name, m) for p in (WEB / "docs").rglob("*.md")
             for m in re.findall(r"JMusicBot-\d+\.\d+\.\d+\.jar", p.read_text(encoding="utf-8")) if m != jar]
    assert stale == [], f"docs name an old jar; the release is {jar}"


def test_docker_pin_matches_release():
    pins = re.findall(r"ghcr\.io/arif-banai/musicbot:(\d+\.\d+\.\d+)", (WEB / "docs/setup/docker.md").read_text(encoding="utf-8"))
    assert pins and set(pins) == {release_version()}


def test_sources_clip_explains_its_prefix(site_dir):
    # The clip was captured on a server whose extra prefix is "!!"; a default install has none (altPrefix = NONE).
    fig = soup(built(site_dir, "features/sources")).select_one("figure.oa-media")
    cap = fig.select_one("figcaption")
    assert cap is not None
    text = cap.get_text(" ", strip=True)
    assert "!!" in text and "@JMusicBot play" in text
