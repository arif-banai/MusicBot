# Docs Website, Phase 1 (scaffold, theme, deploy) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A working docs site in `website/`: the full page outline (one real page, the rest stubs), the "On air" theme, the custom home page with a now-playing replica, a test suite run against the built site, and a CI workflow that builds PRs and deploys `master` to GitHub Pages. It ends at checkpoint 1, where the owner reviews the real built site before any content is written.

**Architecture:** Markdown pages built by Zensical from a `mkdocs.yml` that stays compatible with mkdocs-material 9.8.1. The theme is CSS custom properties mapped onto Material's `--md-*` variables, plus two template overrides (`main.html` for fonts and meta, `home.html` for the home page). Tests are pytest checks over the built HTML in `website/site/`. A session fixture runs one strict build.

**Tech Stack:** Zensical 0.1.1, Python 3.12, pytest 9.2.0, beautifulsoup4 4.15.0, GitHub Actions (`actions/checkout@v6`, `actions/setup-python@v7`, `actions/upload-artifact@v6`, `actions/upload-pages-artifact@v5`, `actions/deploy-pages@v5`).

**Spec:** `docs/superpowers/specs/2026-11-16-docs-website-design.md`

**Scope:** Phase 1 only. Phases 2 (content), 3 (media) and 4 (launch: README, `CNAME`, enabling Pages) get their own plans after checkpoint 1, because the owner's review there can change the theme and templates they build on.

**Spike results (2026-11-17, Zensical 0.1.1):**
- `custom_dir` overrides and `template: home.html` front matter work.
- `extra_css` works.
- Output links are relative (`./…`, `../../…`), so subpath hosting works.
- `zensical build -s` aborts on a broken Markdown page link but **not** on a missing image file. Task 1's asset test covers that gap.

## Global Constraints

- Site URL `https://arif-banai.github.io/MusicBot/`. No `href`/`src` in built pages may start with `/` (protocol-relative `//` and `#` are fine).
- Config stays in `website/mkdocs.yml`. Don't use `hooks`, `exclude_docs`, `draft_docs`, `not_in_nav`, `remote_branch`, `remote_name`, or `navigation.instant`.
- Exact pins: `zensical==0.1.1`, `pytest==9.2.0`, `beautifulsoup4==4.15.0`.
- Dark tokens, verbatim from the spec:
  - Midnight `#171C33` (background)
  - Panel `#222A4A` (raised surfaces)
  - Line `#313A60` (borders)
  - Fog `#A9B1D0` (secondary text)
  - Paper `#F3F5FB` (primary text)
  - Amber `#F5B83D` (playing/active, primary button, warnings)
  - Mint `#4CD6A0` (tips/success)
- Light mode: background around `#F5F6FA`, text around `#1C2238`. Amber and mint keep their meanings and are darkened to pass WCAG AA.
- Fonts: Unbounded 500/700 (headings), Onest 400/500/600 (body), JetBrains Mono (code), from Google Fonts. Set `theme.font: false`.
- Dark is the default scheme (first palette entry is `slate`).
- Copy:
  - Plain language, sentence case, active voice, no hype.
  - Examples use `/play` or `@JMusicBot play`, never `!play`. The default prefix is `@mention`.
  - No `jmusicbot.com` links.
  - YouTube settings are documented as symptom → setting only, never how they work. No "bypass" wording.
  - No ToS or "at your own risk" notes.
- Credit the original project: "JMusicBot by jagrosh", linking `https://github.com/jagrosh/MusicBot`, Apache 2.0.
- Only one thing on a page animates on its own: the home page's now-playing replica. It doesn't animate when `prefers-reduced-motion: reduce` is set.
- The Java build and its CI must not change.

## Review Focus

1. **Asset paths on deeper pages.** An image or CSS path that works at `/` but 404s at `/MusicBot/setup/bot-token/`. Covered by `test_every_local_reference_resolves` and `test_no_root_absolute_references` (Task 1), run over every built page.
2. **Light mode legibility.** Amber and mint on a pale background fall below AA. Covered by `test_token_contrast` (Task 2).
3. **Reduced motion.** The replica keeps ticking for readers who asked for no motion. Covered by `test_now_playing_respects_reduced_motion` (Task 3).
4. **Copy drift.** `!play`, `jmusicbot.com` or "bypass" creeping into pages. Covered by `test_forbidden_copy` (Task 4).
5. **Narrow screens.** The two-column hero or the button rows overflow at 360px. Covered by `test_home_has_mobile_breakpoint` (Task 3), plus the 360px screenshot at checkpoint 1 (Task 6).

---

### Task 1: Toolchain, config skeleton and site tests

**Files:**
- Create: `website/requirements.txt`, `website/mkdocs.yml`, `website/docs/index.md`, `website/README.md`
- Create: `website/tests/conftest.py`, `website/tests/sitecheck.py`, `website/tests/test_build.py`
- Modify: `.gitignore` (append)

**Interfaces:**
- Produces: the session fixture `site_dir() -> pathlib.Path`, which runs `python -m zensical build -s -f mkdocs.yml` with cwd `website/` once per session and returns `website/site`. The fixture fails the test session with the build's output if the build exits non-zero.
- Produces, in `sitecheck.py`:
  - `html_pages(site: Path) -> list[Path]`: every `*.html` file except `404.html`.
  - `soup(path: Path) -> bs4.BeautifulSoup`
  - `local_refs(page: Path) -> list[str]`: every `href`/`src` value that isn't `http(s):`, `mailto:`, `//`, `#…` or empty.
  - `resolve_ref(page: Path, ref: str, site: Path) -> Path | None`: strips the query and fragment, resolves against `page.parent`, maps a directory to `index.html`, and returns `None` if nothing exists.
  - `page_text(page: Path) -> str`: the visible text of `<main>` or `.md-content`.

- [ ] **Step 1: Write `website/requirements.txt`, `.gitignore` additions and a minimal `mkdocs.yml`**

`requirements.txt`: the three pins from Global Constraints.

Append to `.gitignore`:

```
# Docs website
!website/requirements.txt
/website/site/
/website/.cache/
.superpowers/
```

`mkdocs.yml` for now:
- `site_name: JMusicBot`
- `site_url`
- `theme.name: material`
- `docs_dir: docs`
- `nav: [Home: index.md]`

`docs/index.md` is `# JMusicBot`.

- [ ] **Step 2: Write the failing tests in `website/tests/test_build.py`**

```python
def test_site_builds(site_dir):
    assert (site_dir / "index.html").is_file()

def test_no_root_absolute_references(site_dir):
    bad = [(p, r) for p in html_pages(site_dir) for r in local_refs(p) if r.startswith("/")]
    assert bad == []

def test_every_local_reference_resolves(site_dir):
    missing = [(p.relative_to(site_dir), r) for p in html_pages(site_dir)
               for r in local_refs(p) if resolve_ref(p, r, site_dir) is None]
    assert missing == []

def test_content_images_have_alt_text(site_dir):
    bad = [(p, i.get("src")) for p in html_pages(site_dir)
           for i in soup(p).select(".md-content img") if not (i.get("alt") or "").strip()]
    assert bad == []
```

Then write `conftest.py` and `sitecheck.py` to the interfaces above.

- [ ] **Step 3: Prove the resolver catches a missing image**

Temporarily add `![x](missing.png)` to `docs/index.md`.

Run: `cd website && python -m venv .venv && .venv/Scripts/python -m pip install -r requirements.txt && .venv/Scripts/python -m pytest -q` (on Linux, use `.venv/bin/python`).

Expected: `test_every_local_reference_resolves` FAILS, naming `missing.png`. The build itself passes, which is the spike's known gap. Remove the line.

- [ ] **Step 4: Run the suite**

Run: `.venv/Scripts/python -m pytest -q`
Expected: 4 passed.

- [ ] **Step 5: Write `website/README.md`**

Cover:
- Setup (venv, then `pip install -r requirements.txt`).
- `python -m zensical serve` (preview at `http://localhost:8000/MusicBot/`).
- `python -m pytest`.
- Why Zensical, and the fallback: `pip install mkdocs-material==9.8.1`, then `mkdocs build`.
- The media placeholder convention from Task 4.

Add `.venv/` to the `.gitignore` block.

- [ ] **Step 6: Commit**

```bash
git add .gitignore website/
git commit -m "feat(docs): scaffold website with zensical and built-site tests"
```

---

### Task 2: The "On air" theme

**Files:**
- Create: `website/docs/stylesheets/on-air.css`, `website/overrides/main.html`
- Create: `website/docs/assets/images/logo.svg` (amber note mark from the mockup; fill `#F5B83D`), `website/docs/assets/images/favicon.svg`
- Modify: `website/mkdocs.yml`
- Test: `website/tests/test_theme.py`

**Interfaces:**
- Consumes: `site_dir`, `soup`, `html_pages` (Task 1).
- Produces: the CSS custom properties below, defined in both `[data-md-color-scheme="slate"]` and `[data-md-color-scheme="default"]`. Later tasks style with these names only.
  - `--oa-bg`, `--oa-surface`, `--oa-line`
  - `--oa-text`, `--oa-text-muted`
  - `--oa-accent`, `--oa-on-accent`, `--oa-tip`, `--oa-danger`

  The slate values are the dark tokens. `--oa-on-accent` is `#1A1606` in dark mode, and `--oa-danger` is `#DA373C` (Discord red).
- Produces: the `.oa-media` figure styling (rounded frame, soft shadow, max width 100%), and `.oa-media--pending` (dashed `--oa-line` border, `--oa-text-muted` label, min-height 160px).

- [ ] **Step 1: Write the failing tests in `website/tests/test_theme.py`**

```python
TOKENS = ["--oa-bg","--oa-surface","--oa-line","--oa-text","--oa-text-muted",
          "--oa-accent","--oa-on-accent","--oa-tip","--oa-danger"]

def test_dark_is_default_scheme(site_dir):
    assert soup(site_dir / "index.html").body["data-md-color-scheme"] == "slate"

def test_dark_tokens_match_spec():
    t = scheme_tokens("slate")
    assert (t["--oa-bg"], t["--oa-surface"], t["--oa-line"], t["--oa-text-muted"],
            t["--oa-text"], t["--oa-accent"], t["--oa-tip"]) == (
            "#171C33","#222A4A","#313A60","#A9B1D0","#F3F5FB","#F5B83D","#4CD6A0")

@pytest.mark.parametrize("scheme", ["slate", "default"])
def test_token_contrast(scheme):
    t = scheme_tokens(scheme)
    assert set(TOKENS) <= t.keys()
    assert contrast(t["--oa-text"], t["--oa-bg"]) >= 7.0
    assert contrast(t["--oa-text-muted"], t["--oa-bg"]) >= 4.5
    assert contrast(t["--oa-accent"], t["--oa-bg"]) >= 4.5
    assert contrast(t["--oa-on-accent"], t["--oa-accent"]) >= 4.5
    assert contrast(t["--oa-tip"], t["--oa-bg"]) >= 3.0

def test_fonts_loaded_every_page(site_dir):
    for p in html_pages(site_dir):
        hrefs = " ".join(l.get("href","") for l in soup(p).select('link[rel=stylesheet]'))
        assert all(f in hrefs for f in ("Unbounded", "Onest", "JetBrains+Mono")), p

def test_theme_css_linked_every_page(site_dir):
    for p in html_pages(site_dir):
        assert any("stylesheets/on-air.css" in l.get("href","") for l in soup(p).select("link")), p
```

In the test module:
- `scheme_tokens(scheme) -> dict[str, str]` regex-parses the `[data-md-color-scheme="<scheme>"] { … }` block of `on-air.css` into a property → uppercase hex map.
- `contrast(a, b) -> float` is the WCAG 2.x relative-luminance ratio.

- [ ] **Step 2: Run them**

Run: `.venv/Scripts/python -m pytest -q tests/test_theme.py`
Expected: FAIL (`on-air.css` doesn't exist).

- [ ] **Step 3: Write the theme**

`mkdocs.yml`:
- `theme.custom_dir: overrides`, `font: false`
- `logo: assets/images/logo.svg`, `favicon: assets/images/favicon.svg`
- Palette:
  - Entry 1: `scheme: slate`, `primary: custom`, `accent: custom`, toggle "Switch to light mode". No `media` key on either entry, so a light-OS visitor still starts in dark.
  - Entry 2: `scheme: default`, `primary: custom`, `accent: custom`, toggle "Switch to dark mode".
- Features: `navigation.sections`, `navigation.top`, `navigation.footer`, `content.code.copy`, `search.highlight`.
- `extra_css: [stylesheets/on-air.css]`
- `markdown_extensions`: `admonition`, `attr_list`, `md_in_html`, `pymdownx.details`, `pymdownx.superfences`, `pymdownx.highlight`, `pymdownx.inlinehilite`, `toc` (with `permalink: true`).
- `repo_url: https://github.com/arif-banai/MusicBot`, `repo_name: arif-banai/MusicBot`, `edit_uri: edit/master/website/docs/`
- `copyright`: "JMusicBot by <a href="https://github.com/jagrosh/MusicBot">jagrosh</a>. This fork is maintained by arif-banai. Apache 2.0."
- `extra.social`: GitHub `https://github.com/arif-banai/MusicBot`, Discord `https://discord.gg/cyyUxNmmx6`.

`overrides/main.html`:
- `{% extends "base.html" %}`
- In `extrahead`: Google Fonts preconnect, plus one stylesheet link for `Unbounded:wght@500;700`, `Onest:wght@400;500;600`, `JetBrains+Mono:wght@400;500`.
- `og:title`, `og:description` and `og:image` (logo), with paths relative via `config.site_url`.

`on-air.css`:
- The two token blocks. Pick light values that pass `test_token_contrast`.
- Map the tokens onto Material:
  - `--md-default-bg-color` ← `--oa-bg`; `--md-default-fg-color` ← `--oa-text`
  - Header and footer ← `--oa-surface`
  - `--md-accent-fg-color` and `--md-typeset-a-color` ← `--oa-accent`
  - `--md-code-bg-color` ← a darker panel
  - `--md-text-font: "Onest"`, `--md-code-font: "JetBrains Mono"`
- Headings use `Unbounded`, with `letter-spacing: -0.02em` from `h1` to `h2`.
- The active nav link gets `border-left: 2px solid var(--oa-accent)`.
- Admonitions:
  - `note` and `tip` → `--oa-tip`
  - `warning` → `--oa-accent`
  - `danger` → `--oa-danger`
- `.md-typeset` max-width is `75ch`.
- Style `.oa-media` and `.oa-media--pending`.

- [ ] **Step 4: Run the suite**

Run: `.venv/Scripts/python -m pytest -q`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add website/
git commit -m "feat(docs): on-air theme tokens, fonts and Material mapping"
```

---

### Task 3: Home page and now-playing replica

**Files:**
- Create: `website/overrides/home.html`, `website/docs/stylesheets/home.css`, `website/docs/javascripts/now-playing.js`
- Modify: `website/docs/index.md` (front matter `template: home.html`, plus a `hide: [navigation, toc]` list), `website/mkdocs.yml` (`extra_css` adds `stylesheets/home.css`; `extra_javascript: [javascripts/now-playing.js]`)
- Test: `website/tests/test_home.py`

**Interfaces:**
- Consumes: the `--oa-*` tokens and `.oa-media` (Task 2), and `site_dir`/`soup` (Task 1).
- Produces:
  - `home.html` extends `main.html` and overrides `block content`, then renders `{{ page.content }}` after the hero.
  - Feature rows are written as Markdown in `index.md` using `<div class="oa-feature" markdown>` blocks, so phase 2 edits rows without touching the template.
  - Replica markup root: `<div class="oa-np" role="img" aria-label="…">`. The button rows are `.oa-np__row` elements, each holding `.oa-np__btn` elements.
  - `now-playing.js` exposes nothing global. It runs only if `.oa-np` exists.

The replica copies the bot's full layout from `MessageFormatter.buildFullNowPlayingMessage`, with `nowPlaying.showProgressBar = true`:
- Embed author: a server name ("Lounge Server")
- Title: link-coloured "Daft Punk - Something About Us"
- Description, line 1: `▶ ▬▬▬▬🔘▬▬▬▬▬▬▬ [01:23/03:51] 🔊`. The bar is 12 segments with `🔘` at `floor(pos/dur*12)`, as in `FormatUtil.progressBar`.
- Description, line 2: `Source: youtube`
- Fields: Author "Daft Punk"; Duration "03:51"; Queue "4"; Volume "70%"; Requester "@you"
- Button row 1: Prev, Pause (primary), Skip, Stop (danger)
- Button row 2: Shuffle, Repeat, Favorite, Vol -, Vol +

Each button keeps its emoji, as in `applyNowPlayingButtons`.

`now-playing.js` advances the position one second per second from 01:23 to 03:51, zero-padding minutes like `TimeUtil.formatTime`, then loops. It updates the time and the `🔘` position. It does nothing when `matchMedia('(prefers-reduced-motion: reduce)').matches`.

This replaces the mockup's smooth amber bar with the bot's real text bar, so the home page shows what users actually get. The amber accent goes on the stage and the primary path card instead. Flag this at checkpoint 1.

- [ ] **Step 1: Write the failing tests in `website/tests/test_home.py`**

```python
def test_headline(site_dir):
    assert soup(site_dir/"index.html").select_one("h1").get_text(strip=True) == "The music bot you run yourself"

def test_path_cards_link_to_setup_and_migrating(site_dir):
    cards = [(a.get_text(" ", strip=True), a["href"].rstrip("/"))
             for a in soup(site_dir/"index.html").select("a.oa-path")]
    assert any(t.startswith("Set up a new bot") and h.endswith("setup") for t, h in cards)
    assert any(t.startswith("Coming from JMusicBot?") and h.endswith("migrating") for t, h in cards)

def test_replica_buttons_match_bot(site_dir):
    rows = [[b.get_text(" ", strip=True).split(" ", 1)[-1] for b in r.select(".oa-np__btn")]
            for r in soup(site_dir/"index.html").select(".oa-np__row")]
    assert rows == [["Prev","Pause","Skip","Stop"], ["Shuffle","Repeat","Favorite","Vol -","Vol +"]]

def test_replica_is_labelled_image(site_dir):
    np = soup(site_dir/"index.html").select_one(".oa-np")
    assert np["role"] == "img" and "now-playing" in np["aria-label"].lower()

def test_now_playing_respects_reduced_motion():
    assert "prefers-reduced-motion" in (WEB/"docs/javascripts/now-playing.js").read_text()
    assert "prefers-reduced-motion" in (WEB/"docs/stylesheets/home.css").read_text()

def test_home_has_mobile_breakpoint():
    css = (WEB/"docs/stylesheets/home.css").read_text()
    assert re.search(r"@media[^{]*max-width:\s*(7[0-9]{2}|8[0-9]{2}|9[0-9]{2})px", css)

def test_footer_credits_original(site_dir):
    f = soup(site_dir/"index.html").select_one("footer")
    assert f.select_one('a[href="https://github.com/jagrosh/MusicBot"]') and "Apache 2.0" in f.get_text()
```

`WEB` is `Path(__file__).parents[1]`.

The path-card test needs `setup/index.md` and `migrating.md` to exist. Task 4 creates them, so for now add those two files as one-line stubs in this task (Task 4 adds the stub admonition).

- [ ] **Step 2: Run them**

Run: `.venv/Scripts/python -m pytest -q tests/test_home.py`
Expected: FAIL (no `h1` match, no `.oa-np`).

- [ ] **Step 3: Build the hero, replica and feature-row styles**

Layout comes from the picked mockup (direction A, `.superpowers/brainstorm/*/content/visual-direction.html`):
- Two columns, `1.05fr 1fr`, which stack below the breakpoint.
- Headline in Unbounded 700, about `46px`, max `13ch`.
- Lede "Play YouTube, SoundCloud, radio streams and your own files in Discord. No API keys and no subscription: you need Java and a bot token."
- Path cards `a.oa-path`:
  - "Set up a new bot", note "About 10 minutes, from token to first song", amber.
  - "Coming from JMusicBot?", note "What changed and how to move your config".
- The replica sits on an `--oa-surface` stage.

The replica keeps Discord's own colors (`#313338` message, `#2B2D31` embed, `#5865F2` primary, `#4E5058` secondary, `#DA373C` danger) in both site schemes.

In `index.md`, add two `.oa-feature` rows:
- Now-playing panel → `features/now-playing.md`
- yt-dlp library → `features/ytdlp-library.md`

Each row has a `.oa-media--pending` figure. Phase 2 adds the rest.

- [ ] **Step 4: Run the suite**

Run: `.venv/Scripts/python -m pytest -q`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git add website/
git commit -m "feat(docs): home page with live now-playing replica"
```

---

### Task 4: Page outline, stubs and the "Get a bot token" page

**Files:**
- Create stubs, each holding `# <Title>` plus the stub admonition below:
  - `website/docs/setup/{index,java,user-id,invite,first-run,docker,service,hosting}.md`
  - `website/docs/features/{slash-commands,now-playing,queue-history-playlists,ytdlp-library,dj-role,sources,gui,lyrics}.md`
  - `website/docs/reference/{commands,config,proxy,performance,wont-add}.md`
  - `website/docs/migrating.md`
  - `website/docs/help/{troubleshooting,faq}.md`
- Create: `website/docs/setup/bot-token.md` (real content)
- Modify: `website/mkdocs.yml` (`nav`)
- Test: `website/tests/test_pages.py`

**Interfaces:**
- Consumes: `site_dir`, `html_pages`, `page_text`, `soup` (Task 1); `.oa-media--pending` (Task 2).
- Produces:
  - The URL slugs above, which are fixed once live.
  - The stub marker: a `!!! note "Being written"` admonition with the body "This page is part of the new docs and is still being written."
  - The media placeholder convention used by phases 2–3:
    ```
    <figure class="oa-media oa-media--pending" markdown>
    Screenshot: <what it shows>
    </figure>
    ```

`nav` (titles as shown):
- Home
- Setup:
  - Overview
  - Install Java 25
  - Get a bot token
  - Find your user ID
  - Invite the bot
  - First run
  - Run with Docker
  - Run as a service
  - Hosting
- Features:
  - Slash commands
  - Now-playing panel
  - Queue, history and playlists
  - yt-dlp library
  - DJ role and permissions
  - Audio sources
  - Desktop app
  - Lyrics
- Reference:
  - Commands
  - config.txt
  - Proxy
  - Performance tuning
  - Things that won't be added
- Coming from JMusicBot
- Help:
  - Troubleshooting
  - FAQ

- [ ] **Step 1: Write the failing tests in `website/tests/test_pages.py`**

```python
EXPECTED = ["setup/index","setup/java","setup/bot-token","setup/user-id","setup/invite","setup/first-run",
  "setup/docker","setup/service","setup/hosting","features/slash-commands","features/now-playing",
  "features/queue-history-playlists","features/ytdlp-library","features/dj-role","features/sources",
  "features/gui","features/lyrics","reference/commands","reference/config","reference/proxy",
  "reference/performance","reference/wont-add","migrating","help/troubleshooting","help/faq"]

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
```

- [ ] **Step 2: Run them**

Run: `.venv/Scripts/python -m pytest -q tests/test_pages.py`
Expected: FAIL (pages missing).

- [ ] **Step 3: Create the stubs and `nav`, then write `setup/bot-token.md`**

Sections of the bot-token page:
1. **What the token is.** One paragraph: it's the password your copy of the bot uses to log in to Discord.
2. **Create the application.** The Developer Portal (`https://discord.com/developers/applications`), New Application, name it, agree, Create. Placeholder: "Screenshot: Developer Portal, New Application dialog".
3. **Copy the token.** Bot tab, then Reset Token, then Copy. The token is shown once. Placeholder: "Screenshot: Bot tab with Reset Token and Copy (token blurred)".
4. **Turn on Message Content Intent.** Bot tab → Privileged Gateway Intents. A `!!! warning` (amber) explains that if it's off, Discord refuses the bot's connection ("Your bot is missing required Discord intents!", `StartupLifecycleListener`), so no commands work. It's the only privileged intent the bot needs (`JMusicBot.java` requests only `MESSAGE_CONTENT` among the privileged ones). Placeholder: "Screenshot: Privileged Gateway Intents with Message Content on".
5. **Put it in config.txt.** A `hocon` code block with `discord { token = "paste-your-token-here"  owner = 123456789012345678 }`. Link to Find your user ID.
6. **Keep it secret.** A `!!! danger`: anyone with the token controls the bot; reset it if it leaks.

Next-step link: Invite the bot.

Mark the Portal button names as "verify at capture" in an HTML comment. Phase 3 checks them against the live Portal.

- [ ] **Step 4: Run the suite**

Run: `.venv/Scripts/python -m pytest -q`
Expected: all pass, including Task 1's resolver over every new page.

- [ ] **Step 5: Commit**

```bash
git add website/
git commit -m "feat(docs): page outline with stubs and the bot token guide"
```

---

### Task 5: CI build and Pages deploy

**Files:**
- Create: `.github/workflows/docs.yml`
- Test: the workflow's own run, plus a local YAML parse

**Interfaces:**
- Consumes: `website/requirements.txt`, the pytest suite (Tasks 1–4).
- Produces:
  - Job `build`: always runs; uploads the artifact `docs-site` (the built site, kept 14 days) for owner previews.
  - Job `deploy`: runs only for `push` or `workflow_dispatch` on `refs/heads/master`.

The workflow:
- `name: Docs`
- Triggers:
  - `pull_request` on branches `[master, 'release/**']`
  - `push` on branches `[master]`
  - Both use paths `['website/**', '.github/workflows/docs.yml']`
  - `workflow_dispatch`
- `concurrency`: `group: docs-${{ github.ref }}`, `cancel-in-progress: true`
- `permissions: contents: read` at the top level.
- `build` (`ubuntu-latest`, `timeout-minutes: 10`):
  1. checkout@v6
  2. setup-python@v7 with `python-version: '3.12'` and `cache: pip` (`cache-dependency-path: website/requirements.txt`)
  3. `pip install -r website/requirements.txt`
  4. `cd website && python -m pytest -q` (the fixture builds the site)
  5. upload-artifact@v6 `docs-site` from `website/site`
  6. If it will deploy: upload-pages-artifact@v5 with `path: website/site`
- `deploy`:
  - `needs: build`
  - `if: github.ref == 'refs/heads/master' && github.event_name != 'pull_request'`
  - `environment: { name: github-pages, url: ${{ steps.deployment.outputs.page_url }} }`
  - `permissions: { pages: write, id-token: write }`
  - Step: deploy-pages@v5 with `id: deployment`

- [ ] **Step 1: Write the workflow**

- [ ] **Step 2: Validate it locally**

Run: `.venv/Scripts/python -c "import yaml,sys; d=yaml.safe_load(open('../.github/workflows/docs.yml')); print(sorted(d['jobs']))"` (PyYAML comes with Zensical; if not, `pip install pyyaml` into the venv, not into requirements).
Expected: `['build', 'deploy']`.

If `actionlint` is available, run it as well. Expected: no findings.

- [ ] **Step 3: Confirm the Java CI is untouched**

Run: `git diff --stat origin/master -- .github/workflows/ ':!.github/workflows/docs.yml' pom.xml src/`
Expected: empty.

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/docs.yml
git commit -m "ci(docs): build docs on PRs and deploy master to GitHub Pages"
```

Pushing the branch, and seeing the `build` job go green on a PR, waits for the owner's OK at checkpoint 1. Enabling Pages is phase 4.

---

### Task 6: Checkpoint 1, owner review of the real site

**Files:** none committed. Screenshots go to the scratchpad.

- [ ] **Step 1: Serve locally**

Run, in the background: `cd website && .venv/Scripts/python -m zensical serve`
Expected: the site is reachable at `http://localhost:8000/MusicBot/`. Use whatever URL the serve output prints.

- [ ] **Step 2: Capture eight screenshots with Claude in Chrome**

Pages: Home, and Setup → Get a bot token. Schemes: dark and light. Widths: 1280px and 360px.

At 360px, check for horizontal scroll by running `document.documentElement.scrollWidth <= innerWidth` through the JS tool. Expected: `true` on both pages.

- [ ] **Step 3: Show the owner**

Give the owner the local URL and the screenshots. Call out:
- The replica uses the bot's real text progress bar instead of the mockup's smooth bar.
- The light-mode amber and mint values.
- The stub pages in the nav.

Record their changes as new tasks, or fold them into the phase 2 plan.

- [ ] **Step 4: Ask before pushing**

Ask whether to push `feature/docs-website` and open a draft PR, so the `Docs` build runs in CI and its `docs-site` artifact can be downloaded.
