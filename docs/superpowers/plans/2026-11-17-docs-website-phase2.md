# Docs Website, Phase 2 (content) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every "Being written" stub in `website/docs/` with real content. Each page is checked against its source of truth, and the commands and config reference pages are checked against the code automatically, so they can't drift. The home page gets one feature row per major feature. The phase ends at checkpoint 2, the owner's content review.

**Architecture:** Markdown pages only, plus new pytest checks in `website/tests/test_content.py`. Phase 1 already built the theme, templates and CI, and this plan doesn't change them. Each page's facts come from the source listed in the spec's outline table. Media stays as `.oa-media--pending` placeholders, which phase 3 replaces.

**Tech Stack:** As phase 1 (Zensical 0.1.1, pytest 9.2.0, beautifulsoup4 4.15.0). For the verify runs: Java 25 and Maven (`mvn -q -DskipTests package` gives `target/JMusicBot-*-All.jar`).

**Spec:** `docs/superpowers/specs/2026-11-16-docs-website-design.md` (outline table: "Site outline and sources of truth")

**Prior plan:** `docs/superpowers/plans/2026-11-17-docs-website-phase1.md` (done; draft PR #96)

## Global Constraints

Everything in phase 1's Global Constraints applies. Restating the content rules:

- **Copy:**
  - Plain language, sentence case, active voice, no hype.
  - Command examples use `/play` or `@JMusicBot play`, never `!play`. The default prefix is `@mention` (`commands.prefix`).
  - No `jmusicbot.com` links. Link upstream only as `https://github.com/jagrosh/MusicBot` for credit.
- **YouTube-related settings** (`playback.youtube.useOAuth`, `proxy.*`, `ytdlp.*`): one plain line pairing a symptom with its setting, e.g. "If YouTube asks you to sign in, set `useOAuth = true`". Never explain why or how a setting gets around YouTube. No "bypass", no "regional restrictions" wording, no ToS or "own risk" notes.
- **Facts come from the code or a real run, never from upstream's old wiki alone.** Upstream text (`git fetch upstream gh-pages`, then `git show FETCH_HEAD:docs/<page>.md`) may be adapted with credit, but every claim in it gets re-checked against this fork.
- **The release documented is the latest published one** (0.8.0, or 0.8.1 if it ships first). Jar names in examples use `JMusicBot-<version>-All.jar` with the real current version, never 0.6.2.
- **Pages:**
  - Every page ends with a "Next:" link where there's an obvious next step.
  - Every image placeholder says what the capture will show.
  - Lines stay under about 75 characters in rendered width (the theme handles this; don't use wide tables except in Reference).
- **Phase 1's tests stay green.** `test_forbidden_copy`, `test_no_links_to_jmusicbot_com` and `test_every_local_reference_resolves` run over every new page.

## Review Focus

1. **Command reference drifting from the code.** A command that exists but isn't documented, or is listed under the wrong permission level. Covered by `test_commands_page_covers_every_command` (Task 1).
2. **Config reference drifting from `reference.conf`.** A key added in code and missing from the page. Covered by `test_config_page_covers_every_key` (Task 1).
3. **Setup steps that don't work on a clean machine.** First run, invite permissions, Docker first start. Covered by the verify steps in Task 2, whose outputs are recorded in the ledger. They are not asserted by pytest, because they need Discord or Docker.
4. **Migration claims that aren't true.** The automatic config migration in particular. Covered by Task 4's verify step, which runs the migration on an upstream-format `config.txt`.
5. **YouTube copy crossing the line.** Covered by `test_youtube_settings_are_symptom_only` (Task 3), plus the existing forbidden-copy tests.

---

### Task 1: Reference pages (Commands, config.txt, Proxy, Performance tuning, Things that won't be added)

**Files:**
- Modify: `website/docs/reference/{commands,config,proxy,performance,wont-add}.md`
- Create: `website/tests/test_content.py`

**Interfaces:**
- Consumes: `site_dir`, `page_text`, `soup` (phase 1, `tests/sitecheck.py`).
- Produces, in `test_content.py`:
  - `REPO = Path(__file__).resolve().parents[2]`
  - `built(site_dir, slug) -> Path` (same rule as `test_pages.built`; import it from there)
  - `assert_written(site_dir, slug)`: asserts the page exists, has no "Being written" note, and has at least 120 words of visible text.
  - `prefix_commands() -> dict[str, str]`: the top-level command name → category (`general|music|dj|admin|owner`). It reads the first `this.name = "<x>"` in each `src/main/java/com/jagrosh/jmusicbot/commands/v1/<category>/*Cmd.java`. Inner subcommand classes come later in the file and are ignored.
  - `slash_commands() -> dict[str, str]`: the same, over `commands/v2/<category>/*SlashCmd.java`. Files without a `this.name` are skipped, and that gets a ledger note naming them. `LibraryPanelSlashCmd` and `LibraryUnlinkSlashCmd` map to `"library panel"` and `"library unlink"`.
  - `config_keys() -> list[str]`: dotted leaf keys from `src/main/resources/reference.conf`. The parser is the brace-stack approach: strip `#` comments, `name {` pushes, `}` pops, `name =` records `stack + name`. It excludes `meta.*` and `commands.aliases.*`.

The commands page is grouped by permission level, in this order: General, Music, DJ, Admin, Owner. Each group is one table:

| Command | Slash | What it does | Aliases |
|---|---|---|---|

Aliases come from `commands.aliases` in `reference.conf`. Owner-only commands are listed with a one-line warning for `eval`, which runs arbitrary code. Its "What it does" text quotes `dangerous.eval` being off by default. Prefix-only commands (e.g. `lyrics`, `scsearch`) show "—" in the Slash column.

The config page follows `reference.conf`'s section order. It has one `##` per top-level block and one row per key: key path in code, default, and what it does, in plain words from the file's own comments. It links the YouTube keys to the Troubleshooting page using the symptom → setting style.

- [ ] **Step 1: Write the failing tests**

```python
def test_commands_page_covers_every_command(site_dir):
    text = page_text(built(site_dir, "reference/commands"))
    missing = [n for n in prefix_commands() if n not in text] + \
              [f"/{n}" for n in slash_commands() if f"/{n}" not in text]
    assert missing == []

def test_commands_grouped_by_permission(site_dir):
    heads = [h.get_text(strip=True) for h in soup(built(site_dir, "reference/commands")).select("h2")]
    assert heads[:5] == ["General", "Music", "DJ", "Admin", "Owner"]

def test_config_page_covers_every_key(site_dir):
    text = page_text(built(site_dir, "reference/config"))
    assert [k for k in config_keys() if k not in text] == []

@pytest.mark.parametrize("slug", ["reference/commands", "reference/config", "reference/proxy",
                                  "reference/performance", "reference/wont-add"])
def test_reference_pages_written(site_dir, slug):
    assert_written(site_dir, slug)

def test_config_key_parser_finds_known_keys():
    keys = config_keys()
    assert {"discord.token", "nowPlaying.showProgressBar", "ytdlp.matching.alignSwitch"} <= set(keys)
    assert not any(k.startswith(("meta.", "commands.aliases.")) for k in keys)
```

Run: `cd website && .venv/Scripts/python -m pytest -q tests/test_content.py`
Expected: the parser test PASSES (it tests the helper). The others FAIL on stubs.

- [ ] **Step 2: Write the five pages**

Sources:
- **Commands:** the v1/v2 classes, each class's `help`/`arguments` strings and the slash `OptionData` descriptions, plus `commands.aliases`.
- **config.txt:** `reference.conf`.
- **Proxy:** README "Proxy Configuration". Drop the "bypass regional restrictions" wording. HTTP proxies only; SOCKS isn't supported.
- **Performance:** README "Performance Tuning", `performance {}`, Docker `JAVA_OPTS`.
- **Won't add:** upstream `things-that-wont-be-added.md`. Keep only items that still hold for this fork, and add an HTML comment `<!-- owner review at checkpoint 2 -->`.

- [ ] **Step 3: Run the suite**

Run: `.venv/Scripts/python -m pytest -q`
Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add website
git commit -m "docs(site): commands, config, proxy, performance and won't-add reference pages"
```

---

### Task 2: Setup pages (Overview, Install Java 25, Find your user ID, Invite the bot, First run, Run with Docker, Run as a service, Hosting)

**Files:**
- Modify: `website/docs/setup/{index,java,user-id,invite,first-run,docker,service,hosting}.md`
- Modify: `website/tests/test_content.py`

**Interfaces:**
- Consumes: `assert_written`, `built`, `REPO` (Task 1).
- Produces: `recommended_permissions() -> list[str]`, the `Permission.X` names in `JMusicBot.RECOMMENDED_PERMS`. The invite page lists each one under its Discord display name (e.g. `MESSAGE_EMBED_LINKS` → "Embed Links"); the mapping table lives in the test.

Page content:
- **Overview:** the five steps as a numbered list (a real sequence), each linking its page, plus a "Rather use Docker?" shortcut.
- **Install Java 25:** Windows, macOS and Linux tabs (`pymdownx.tabbed` with `alternate_style: true`; add the extension to `mkdocs.yml`). Show how to check with `java -version`. Cover Linux glibc ≥ 2.38 (`ldd --version`; Ubuntu 24.04 and Debian 13 ship it) and `libopus0 libsodium23`.
- **Find your user ID:** Developer Mode → Copy User ID. Two placeholders.
- **Invite the bot:**
  - The bot prints its own invite link at startup (`StartupLifecycleListener`); copy that, or use the Developer Portal → OAuth2 URL Generator with scopes `bot` and `applications.commands`.
  - List the permissions from `RECOMMENDED_PERMS`.
  - One placeholder.
- **First run:**
  - What happens with no `config.txt` (verify, Step 2).
  - The `java` command for each OS, including the PowerShell quoting from the README.
  - What the console or GUI shows when the bot is ready.
  - The `-Dnogui=true` flag.
- **Run with Docker:** README Docker section. Cover both new users and existing users moving from a jar, and pinning versus `latest`. The image is `ghcr.io/arif-banai/musicbot`.
- **Run as a service:** systemd unit and Windows (Task Scheduler or NSSM), adapted from upstream `running-as-a-service.md` with Java 25 paths and the README's JVM flags.
- **Hosting:** home machine, VPS or Raspberry Pi-class ARM64 (the Docker image is multi-arch). State the requirements (Java 25, glibc 2.38, some RAM guidance) and don't recommend any provider.

- [ ] **Step 1: Write the failing tests**

```python
SETUP = ["setup/index","setup/java","setup/user-id","setup/invite","setup/first-run",
         "setup/docker","setup/service","setup/hosting"]

@pytest.mark.parametrize("slug", SETUP)
def test_setup_pages_written(site_dir, slug):
    assert_written(site_dir, slug)

def test_java_page_states_requirements(site_dir):
    t = page_text(built(site_dir, "setup/java"))
    assert "Java 25" in t and "2.38" in t and "java -version" in t

def test_invite_page_lists_scopes_and_permissions(site_dir):
    t = page_text(built(site_dir, "setup/invite"))
    assert "applications.commands" in t and "bot" in t
    assert [p for p in recommended_permissions() if PERMISSION_NAMES[p] not in t] == []

def test_docker_page_uses_fork_image(site_dir):
    t = page_text(built(site_dir, "setup/docker"))
    assert "ghcr.io/arif-banai/musicbot" in t and "jagrosh" not in t.lower()

def test_no_stale_jar_versions(site_dir):
    for p in html_pages(site_dir):
        assert "0.6.2" not in page_text(p), p
```

Run: `.venv/Scripts/python -m pytest -q tests/test_content.py`
Expected: the new tests FAIL.

- [ ] **Step 2: Verify the first-run and startup facts**

Run:
```
mvn -q -DskipTests package
```
Then, in an empty temp dir:
```
java -Dnogui=true -jar <repo>/target/JMusicBot-*-All.jar < /dev/null
```
Expected: a record, in the ledger, of what it prints and whether it creates `config.txt`.

Also read `BotConfig.validateRequiredFields` and `StartupLifecycleListener` for the invite-link and missing-config messages. Write First run and Invite from what you saw. If behaviour differs from the README, the run wins; ledger a ruling.

- [ ] **Step 3: Write the eight pages, then run the suite**

Run: `.venv/Scripts/python -m pytest -q`
Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add website
git commit -m "docs(site): setup guide pages"
```

---

### Task 3: Feature pages and Help (eight Features pages, Troubleshooting, FAQ)

**Files:**
- Modify:
  - `website/docs/features/{slash-commands,now-playing,queue-history-playlists,ytdlp-library,dj-role,sources,gui,lyrics}.md`
  - `website/docs/help/{troubleshooting,faq}.md`
- Modify: `website/tests/test_content.py`

**Interfaces:**
- Consumes: `assert_written`, `built`, `config_keys` (Task 1).

Each feature page has these parts, in this order:
1. A one-paragraph "what it is".
2. A placeholder for its clip, using the phase 3 shot list numbers from the spec, e.g. `GIF: /queue with paging buttons`.
3. "How to use it" (commands, with slash and prefix forms).
4. "Settings" (its `config.txt` keys, linking to the config reference anchor).
5. "Who can use it" (everyone, DJ, admin or owner, per the command classes).

Sources: the spec's outline table.

Specific content:
- **yt-dlp library:**
  - Written for server owners. Its source is the README section plus `docs/ytdlp-library.md`'s Overview only, not the contributor internals.
  - Cover: installing yt-dlp and a JavaScript runtime yourself, the Windows path quoting rule, `prompt` vs `always`, the Download button, `/library panel` and `/library unlink`, and matching suggestions with Switch.
  - The YouTube copy rule applies to this page.
- **Lyrics:** prefix-only if `slash_commands()` has no lyrics. The page says so.
- **Troubleshooting:** one `###` per symptom, each with a short "Fix:". Required entries:
  - The bot is online but ignores commands (Message Content intent).
  - "Your bot is missing required Discord intents!"
  - The bot won't start on Linux (glibc 2.38).
  - `UnsupportedClassVersionError` (wrong Java).
  - Garbled non-English characters (`-Dfile.encoding=UTF-8`).
  - Songs won't play or YouTube asks you to sign in: update the bot first, then `useOAuth`, then the yt-dlp library. Symptom → setting only.
  - Slash commands don't show up (wait for Discord to register them, or re-invite with `applications.commands`).
  - Stuttering audio (Performance tuning).
- **FAQ:** built from the fork's GitHub issues and discussions:
  ```
  gh issue list --repo arif-banai/MusicBot --state all --limit 100
  gh api repos/arif-banai/MusicBot/discussions
  ```
  6–10 questions, each answered in two to four sentences with a link to the page that covers it. Add an HTML comment `<!-- owner adds questions at checkpoint 2 -->`.

- [ ] **Step 1: Write the failing tests**

```python
FEATURES = ["features/slash-commands","features/now-playing","features/queue-history-playlists",
            "features/ytdlp-library","features/dj-role","features/sources","features/gui","features/lyrics"]

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
```

Run: `.venv/Scripts/python -m pytest -q tests/test_content.py`
Expected: the new tests FAIL.

- [ ] **Step 2: Write the ten pages, then run the suite**

Run: `.venv/Scripts/python -m pytest -q`
Expected: all pass.

- [ ] **Step 3: Commit**

```bash
git add website
git commit -m "docs(site): feature pages, troubleshooting and FAQ"
```

---

### Task 4: Coming from JMusicBot (migration page)

**Files:**
- Modify: `website/docs/migrating.md`
- Modify: `website/tests/test_content.py`

Sections:
1. **What you need to change:** Java 11 → 25; glibc 2.38 on Linux, or Docker; turn on Message Content Intent.
2. **Your config.txt:** it's moved over automatically on first start. Say what happens to the old file, from what the verify step shows.
3. **Your playlists:** the same `Playlists/` folder and format, if that's true; verify against `PlaylistLoader`.
4. **New things to try:** slash commands, now-playing buttons and layouts, history, the yt-dlp library, Docker, and the desktop app. Each links its page.
5. **What's gone or different:** anything removed or renamed between upstream 0.4.x and now. Get it from `git log upstream/master..origin/master` and the v1 command list compared with upstream's `commands.md`.

Credit the original project in the opening paragraph.

- [ ] **Step 1: Write the failing test**

```python
def test_migrating_page_covers_upgrade_steps(site_dir):
    assert_written(site_dir, "migrating")
    t = page_text(built(site_dir, "migrating"))
    for needle in ("Java 25", "2.38", "Message Content Intent", "config.txt", "/play"):
        assert needle in t, needle
    assert soup(built(site_dir, "migrating")).select_one('a[href="https://github.com/jagrosh/MusicBot"]')
```

Run: `.venv/Scripts/python -m pytest -q tests/test_content.py -k migrating`
Expected: FAIL.

- [ ] **Step 2: Verify the config migration on an upstream-format file**

1. Get upstream's example config: `git show upstream/master:src/main/resources/reference.conf` (the flat format), or copy `src/main/resources/old_configs/reference-legacy.conf`.
2. Fill in a fake token and owner, and put it as `config.txt` in an empty temp dir.
3. Run the jar from Task 2 with `-Dnogui=true`.
4. Record in the ledger:
   - whether the file is rewritten to the nested format,
   - whether a backup is kept, and its file name,
   - the log lines.

The run fails at login with the fake token; that's expected. If the run isn't possible, fall back to the migration unit tests (`mvn -q test -Dtest='*Migration*'`), then ledger which one was used.

- [ ] **Step 3: Write the page, then run the suite**

Run: `.venv/Scripts/python -m pytest -q`
Expected: all pass.

- [ ] **Step 4: Commit**

```bash
git add website
git commit -m "docs(site): migration guide for upgraders from JMusicBot"
```

---

### Task 5: Home feature rows, no stubs left, checkpoint 2

**Files:**
- Modify: `website/docs/index.md`
- Modify: `website/docs/stylesheets/home.css` (alternating rows)
- Modify: `website/tests/test_content.py`, `website/tests/test_home.py`

Home rows, in this order:
1. Now-playing panel
2. Slash commands
3. Queue, history and playlists
4. yt-dlp library
5. DJ role and permissions
6. Desktop app

Each row is a `.oa-feature`, with every second row adding the class `oa-feature--flip` so the media sits on the left. Below 900px rows stack, text first.

- [ ] **Step 1: Write the failing tests**

```python
def test_no_stub_pages_remain(site_dir):
    assert [str(p.relative_to(site_dir)) for p in html_pages(site_dir) if "Being written" in page_text(p)] == []
```

And in `test_home.py`:

```python
def test_home_has_six_feature_rows_alternating(site_dir):
    rows = soup(site_dir/"index.html").select(".oa-feature")
    assert len(rows) == 6
    assert ["oa-feature--flip" in r.get("class", []) for r in rows] == [False, True] * 3
```

Run: `.venv/Scripts/python -m pytest -q`
Expected: `test_home_has_six_feature_rows_alternating` FAILS (2 rows). `test_no_stub_pages_remain` PASSES if Tasks 1–4 are done. If not, it names the leftovers, so finish them first.

- [ ] **Step 2: Add the rows and the flip style**

The flip style:
```css
.md-typeset .oa-feature--flip > :first-child { order: 2; }
```
inside a `min-width: 901px` media query.

Run: `.venv/Scripts/python -m pytest -q`
Expected: all pass.

- [ ] **Step 3: Commit and push**

```bash
git add website
git commit -m "docs(site): home feature rows for every major feature"
git push
```

Pushing to the draft PR's branch `feature/docs-website` is covered by the owner's "continue". Check that the PR's `Docs` workflow `build` job passes:

```
gh pr checks 96 --repo arif-banai/MusicBot
```

- [ ] **Step 4: Checkpoint 2**

1. Serve locally.
2. Give the owner the URL and the PR's `docs-site` artifact.
3. List:
   - every ledger ruling,
   - the verify results (first run, config migration),
   - the pages that need their input: Things that won't be added, FAQ, Coming from JMusicBot.

Their changes become fix-ups before phase 3.
