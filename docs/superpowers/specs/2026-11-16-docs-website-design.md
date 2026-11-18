# Docs website for the JMusicBot fork: design

Date: 2026-11-16
Branch: `feature/docs-website` (worktree `MusicBot-worktrees/docs-website`)

## Intent

The fork still points readers at the original project's site, jmusicbot.com. That site was written for jagrosh's
version, and it no longer matches this fork. The README's Setup links go there, and that setup guide doesn't mention
Java 25, the Message Content intent, glibc 2.38 or the new nested `config.txt`. The fork has also added a lot the old
site never covered: slash commands, now-playing buttons and layouts, the yt-dlp library, Docker, proxy and
performance settings.

We want a documentation site of our own, like jmusicbot.com in spirit: a guided setup, a full reference, and
screenshots and GIFs that show each feature working in Discord.

## Who it's for

1. **New self-hosters**: people new to the bot who want to get it running in their server.
2. **Upgraders from the original JMusicBot**: people who need to know what changed and how to move over.

The home page sends each group down its own path.

## Goals

- A setup path that works from start to finish on Windows, Linux, macOS and Docker, with screenshots for every
  Developer Portal step.
- A Features section where every major feature has a short GIF or screenshot of it working in Discord.
- A complete reference for commands (prefix and slash, by permission level) and `config.txt`, correct for the
  current release (0.8.0).
- One migration page for users coming from the original JMusicBot.
- A troubleshooting page for the common failures: intents, glibc, wrong Java, mojibake, playback errors.
- Docs live next to the code, so a feature PR can update its page in the same PR.

## Non-goals

- Versioned docs (one site per release). The site documents the latest release only.
- A blog, changelog pages or release announcements. GitHub Releases stays the changelog.
- Translations.
- Ads or analytics. Upstream's site ran AdSense; ours doesn't.
- Docs for contributors. `docs/ARCHITECTURE.md`, `docs/DEVELOPMENT_WORKFLOW.md` and `docs/ytdlp-library.md` stay in
  the repo as they are. The site may link to them.

## Anti-goals (would make this a failure even if it works)

- **Wrong setup steps.** A site that looks good but sends a new user down a broken path is worse than the README.
  Every setup and reference claim must be checked against the code or a real run.
- **Details of the YouTube workarounds on public pages.** Keep it low-key: say what to configure, not how YouTube is
  being worked around. This includes the troubleshooting entry for playback errors (403s). **The line (owner,
  2026-11-16):** each related key (`playback.youtube.useOAuth`, `proxy.*`, `ytdlp.*`) gets a plain one-liner pairing
  the symptom with the setting, for example "If YouTube asks you to sign in, set `useOAuth = true`". Troubleshooting
  says to update the bot first, then try those settings. Pages never explain why or how a setting gets around YouTube,
  and the README's "bypass regional restrictions" wording isn't carried over.
- **No "may violate ToS / at your own risk" notes.**
- **Leaking anything in captures:** tokens, client secrets, user IDs, server or member names that aren't from the
  test setup.
- **Copying jagrosh's branding.** No jmusicbot.com domain and no jagrosh logo. Credit the original project in the
  footer and on the migration page instead.

## Constraints

- Hosted on GitHub Pages at `https://arif-banai.github.io/MusicBot/`. That's a subpath, so every link and asset
  must be relative; nothing can start with `/`. Set `site_url` to `https://arif-banai.github.io/MusicBot/`.
- Pages is not enabled on the fork yet (`GET /repos/arif-banai/MusicBot/pages` returns 404). The owner switches it on
  once (Settings → Pages → Source: GitHub Actions).
- The leftover `CNAME` file at the repo root (`jmusicbot.com`) is deleted, so GitHub Pages never tries to claim that
  domain.
- The repo's CI validates branch names: `feature/`, `fix/`, `chore/`, `deps/`, `release/`.
- The repo is Java/Maven. The docs toolchain must not affect the Java build or its CI. It runs only in its own
  workflow, and only when `website/` changes.
- License: Apache 2.0, same as the code. Text adapted from upstream's `gh-pages` docs is allowed with credit. Most
  of it will be rewritten anyway, since much of it is out of date.

## Approaches considered

| | Approach | Trade-off |
|---|---|---|
| **A (picked)** | Markdown docs site in the MkDocs Material style, with a custom theme and home page | Search, dark mode, mobile layout and navigation come built in. Anyone can add a page in Markdown. Inner pages keep Material's layout, so the theme changes how they look but not their structure. |
| B | Astro Starlight | More layout freedom (custom components anywhere), but adds a Node/npm toolchain and `.mdx` files to a Java repo. |
| C | Hand-built HTML | Total control, but search, navigation and dark mode by hand, and every page is HTML. Ruled out for a site of about 20 pages. |

The owner picked **A** because it matches the original site, it's the easiest to keep up alongside the Java code,
and Material's theming allows a distinct look.

### Build tool (builder's call, recorded here)

Material for MkDocs went into maintenance mode on 2025-11-05 (bug and security fixes only), and its team now builds
**Zensical**, a successor that reads `mkdocs.yml`, uses the same Material theme and supports Material template
overrides. As of today Zensical is at 0.1.1 (released 2026-11-10) with frequent releases. mkdocs-material is at 9.8.1
(2026-11-07).

Decision:

- Keep the config in **`mkdocs.yml`** and use only features both tools support, so the same source builds with
  either one.
- Build with **Zensical**, pinned to an exact version in CI.
- If Zensical can't do something the design needs (custom home template, admonition styling, search), fall back to
  `mkdocs-material==9.8.1`, also pinned. The choice is recorded in `website/README.md`.
- Don't use the settings Zensical doesn't support yet: `hooks`, `exclude_docs`, `draft_docs`, `not_in_nav`,
  `remote_branch`, `remote_name`. Deploy goes through GitHub Actions, not `gh-deploy`, so the last two don't matter.

## Visual design: direction A, "On air"

The owner picked this from three mockups. The mockup lives at
`.superpowers/brainstorm/1398-1794841226/content/visual-direction.html`, which is reference only and not committed.

### Tokens

| Token | Dark (default) | Role |
|---|---|---|
| Midnight | `#171C33` | Page background |
| Panel | `#222A4A` | Raised surfaces, nav, cards, the stage behind the hero |
| Line | `#313A60` | Borders and dividers |
| Fog | `#A9B1D0` | Secondary text |
| Paper | `#F3F5FB` | Primary text |
| Amber | `#F5B83D` | What's playing or active: progress, current nav item, primary button, warnings |
| Mint | `#4CD6A0` | Tips and success notes |

Light mode, through Material's toggle, uses a pale background (around `#F5F6FA`) with ink text (around `#1C2238`).
Amber and mint keep their meanings but are darkened until they pass WCAG AA on light. The exact light values are
left to the builder and shown to the owner at checkpoint 1.

### Type

- Headings: **Unbounded** (500/700), wide, with slight negative tracking on large sizes.
- Body: **Onest** (400/500/600).
- Code: **JetBrains Mono**.
- All loaded from Google Fonts. Material's own font loading is turned off (`font: false`), and the fonts load through
  the theme override.

### Home page (custom template)

1. Top bar: logo (amber music-note mark plus "JMusicBot"), nav, GitHub link with the release version.
2. Hero, two columns:
   - Left: headline ("The music bot you run yourself"), one-line lede, and two path cards: **Set up a new bot**
     (amber, with an "about 10 minutes" note) and **Coming from JMusicBot?** (panel style).
   - Right: a **live HTML recreation of the now-playing message** (Discord's colors, the real button set and order
     from `MessageFormatter`, progress bar animating), on a Panel-colored stage. It's HTML rather than a GIF so it's
     sharp at any size. It stops animating when the reader prefers reduced motion.
3. Feature rows: one row per major feature, alternating text and its real GIF or screenshot, each linking to its
   Features page.
4. Footer: credit to the original JMusicBot by jagrosh, the Apache 2.0 license, and Discord and GitHub links.

The progress-bar animation is the page's only motion that plays on its own.

### Docs pages

Material's standard layout (left nav, content, on-this-page list) restyled with the tokens:

- The current nav item gets an amber left border.
- `tip`/`note` callouts use mint, `warning` uses amber, `danger` uses Discord red.
- Code blocks sit on a darker panel with amber keys.
- **Media frame:** Discord screenshots and GIFs sit in a rounded frame with a soft shadow, so they read as Discord
  inside the site.

Text width stays under about 75 characters.

### Copy fixes from the mockup

The mockup's tip said `!play`, but the default prefix is `@mention` (`commands.prefix = "@mention"` in
`reference.conf`). Examples use `/play` (slash) or `@JMusicBot play`, and say that the prefix is configurable.

## Site outline and sources of truth

Each page's facts come from the source listed. Anything marked **verify** is checked by running it before the page
ships.

| Section | Page | Source of truth |
|---|---|---|
| Home | Home (custom template) | This doc |
| Setup | Setup overview (the steps in order, with a Docker shortcut) | Code and README |
| | Install Java 25 (Windows, macOS, Linux) | `pom.xml` release target; **verify** on each OS family where practical |
| | Get a bot token (including Message Content intent) | Developer Portal, captured live |
| | Find your user ID | Discord client, captured live |
| | Invite the bot | Developer Portal OAuth2 URL generator, captured live. Scopes and permissions from the code (`bot`, `applications.commands`, plus the permissions the bot checks) |
| | First run and `config.txt` basics | `reference.conf` and first-run behaviour; **verify** with a clean run |
| | Run with Docker / Docker Compose | README Docker section, `Dockerfile`, `docker-compose.example.yml` |
| | Run as a service (systemd, Windows) | Adapt upstream's `running-as-a-service.md`; **verify** the commands |
| | Hosting (24/7 options) | Adapt upstream's `hosting.md`; update for Java 25 and glibc 2.38 |
| Features | Slash commands | `commands/v2/**` |
| | Now-playing panel (buttons, layouts, progress bar, images, minimal mode) | `MessageFormatter`, `NpButtonsSlashCmd`, `NpLayoutSlashCmd`, `nowPlaying {}` config |
| | Queue, history, playlists | `QueueSlashCmd`, `HistorySlashCmd`, `PlaylistsSlashCmd`, playlist files |
| | yt-dlp library (download button, prompt mode, `/library` panel, suggestions, Switch) | README yt-dlp section, `docs/ytdlp-library.md`, `ytdlp {}` config |
| | DJ role and permissions | `SetdjSlashCmd`, the command validators |
| | Audio sources and formats | `audioSources {}` config, lavaplayer's supported formats |
| | Desktop GUI | `gui {}` config, the FlatLaf GUI |
| | Lyrics | `LyricsCmd` (prefix only; check whether there's a slash version) |
| Reference | Commands (prefix and slash, grouped General / Music / DJ / Admin / Owner, with aliases) | `commands/v1/**`, `commands/v2/**`, `commands.aliases` in `reference.conf` |
| | `config.txt` reference (every key, default, what it does) | `reference.conf` (the comments there are the authority) |
| | Proxy | README proxy section, `proxy {}` config |
| | Performance tuning | README performance section, `performance {}` config, Docker `JAVA_OPTS` |
| | Things that won't be added | Adapt upstream's page; the owner reviews the list |
| Migrating | Coming from the original JMusicBot | Java 11 → 25; glibc 2.38; Message Content intent; flat `config.txt` → nested, migrated automatically (`config/migration/LegacyToV1`, `V1ToV2`: **verify** with an upstream-format config); slash commands; Docker image; what's new |
| Help | Troubleshooting | README, issues and discussions on the fork. Playback-error entries stay low-key (see anti-goals) |
| | FAQ | Owner input plus common questions from issues and Discord |

Upstream's `advanced-eval.md` and `advanced-playlists.md` are carried over only if the features still behave the
same; the builder checks this.

## Media plan

### Capture method (owner's choice)

Claude records the captures with Claude in Chrome: Discord web for bot features, the Developer Portal for setup.

- **Bot features:** use the owner's test server and the local test bot (currently 0.8.1 from `fix/ytdlp-retry-403`;
  switch to the release jar if it's out by then). The owner approves the site and server in Chrome before the first
  capture.
- **Developer Portal:** use a **throwaway application** made just for the docs, never the real bot's application.
  **Never press Reset Token** on the real bot's application, since that would break the running bot. Blur or crop
  tokens, client secrets, application IDs and user IDs.
- **Framing:** crop to the message or panel area. No server list, no member list, and no usernames other than the
  test bot and the owner's own account. The owner chose (2026-11-16) to appear in the captures with their normal
  Discord account; no separate test account.
- **Approval:** each clip goes to the owner before it's committed. Nothing is published unreviewed.

### Formats and budget

- Screenshots: PNG or WebP at 2x, about 1400px wide at most.
- Animations: kept under about 1.5 MB each and 3 to 8 seconds long. Use a looping muted MP4/WebM (`<video autoplay
  loop muted playsinline>`) when it's much smaller than the GIF, with the GIF as the fallback the owner asked for.
- Total committed media: about 40 MB at most. If it grows past that, move media to a release asset or a separate
  branch rather than bloating master.
- Each piece of media gets alt text that says what it shows.

### Shot list (first draft; the builder refines it)

| # | Page | What it shows | Kind |
|---|---|---|---|
| 1 | Home hero | (live HTML, no capture) | none |
| 2 | Get a bot token | New Application dialog | screenshot |
| 3 | Get a bot token | Bot tab: Reset Token / Copy (throwaway app, value blurred) | screenshot |
| 4 | Get a bot token | Privileged Gateway Intents, Message Content on | screenshot |
| 5 | Find your user ID | Developer Mode toggle, Copy User ID | screenshot ×2 |
| 6 | Invite the bot | OAuth2 URL generator with scopes and permissions | screenshot |
| 7 | First run | Console or GUI on first start, `config.txt` generated | screenshot |
| 8 | Slash commands | Typing `/play`: autocomplete, then the song starts | GIF |
| 9 | Now-playing panel | Pressing Pause / Skip / Vol on the panel | GIF |
| 10 | Now-playing panel | Layout options (`/nplayout`) before and after | screenshot ×2 |
| 11 | Queue | `/queue` with paging buttons | GIF |
| 12 | History | `/history`, then Play Now or Save as playlist | GIF |
| 13 | Playlists | `/playlists` browse, then Play Now | GIF |
| 14 | yt-dlp library | Download button on now-playing | GIF |
| 15 | yt-dlp library | `/library` panel: browse and suggestions | GIF |
| 16 | Desktop GUI | FlatLaf window (health and performance panels) | screenshot |
| 17 | Lyrics | Lyrics output | screenshot |

The owner removes anything they don't want shown.

## Repo layout

```
website/
  mkdocs.yml                  # config (Zensical- and Material-compatible subset)
  README.md                   # how to preview/build, which tool and version
  requirements.txt            # pinned docs toolchain
  docs/
    index.md                  # uses the home template
    setup/…  features/…  reference/…  migrating.md  help/…
    assets/images/…  assets/media/…
    stylesheets/on-air.css
  overrides/
    home.html                 # custom home template (hero, now-playing replica, feature rows)
    main.html                 # fonts, meta/OpenGraph tags
.github/workflows/docs.yml    # build on PRs touching website/**; deploy to Pages on master
```

- **CI:** on pull requests that touch `website/**`, build in strict mode (broken links fail). On pushes to `master`
  that touch `website/**` (plus manual dispatch), build and deploy with `actions/upload-pages-artifact` and
  `actions/deploy-pages`.
- **Local preview:** `pip install -r website/requirements.txt`, then `zensical serve` (or `mkdocs serve`) from
  `website/`.
- `.superpowers/` is added to `.gitignore`.

### Changes outside `website/`

- Delete the root `CNAME`.
- README: point the Setup links and banner at the new site, and fix stale bits it shares with the site (the
  `JMusicBot-0.6.2-All.jar` examples). Keep the README shorter and link to the site for details. How much to trim is
  shown to the owner in the PR.
- In the repo's GitHub "About", set the website to the new URL (owner action, or `gh repo edit --homepage` with
  owner OK).

## Phases and checkpoints

1. **Scaffold, theme and deploy.** Toolchain, `mkdocs.yml`, the On air theme, the home template with the
   now-playing replica, and one real docs page (Get a bot token) with placeholder media.
   **Checkpoint 1:** the owner reviews the real built site (local preview, then a Pages preview) in dark and light
   mode before any content is written. This checks that the mockup survives in real Material/Zensical.
2. **Content.** Write every page in the outline from its source of truth; run the **verify** items. Media slots stay
   as labelled placeholders, so the site could ship without media.
   **Checkpoint 2:** the owner reviews the pages, especially Migrating, Troubleshooting and Things that won't be
   added.
3. **Media.** Capture the shot list per the rules above; the owner approves each clip; swap the placeholders.
   **Checkpoint 3:** the owner reviews the media.
4. **Launch.** README and `CNAME` changes, Pages enabled, merge to master, check the live URL, update the repo's
   homepage link.

**Previews and going live (builder's call).** A GitHub Pages project site has one URL and no preview deploys, so
checkpoints 1–3 use the **local preview** (`zensical serve`) plus the CI build artifact (the built site as a
downloadable zip). The deploy job runs only on `master`. The site goes live once, at phase 4, when the docs PR merges.
Pages is switched on just before that merge. If the owner later wants corrected docs live sooner, phases 1–2 can
merge first, together with the README/CNAME changes, so the README never points at a half-built site.

**Which release (builder's call).** The site documents the latest **published** release (0.8.0 today). If 0.8.1 is
published before launch, the reference and captures move to it. Docs PRs target `master`. The PR build also runs on
`release/**` so a release branch can carry its own page updates.

**FAQ (builder's call).** Built from the fork's GitHub issues and discussions, plus anything the owner adds at
checkpoint 2. It isn't a blocker; it can launch short.

**`.gitignore`** ignores `*.txt`, so add `!website/requirements.txt`.

## Left to the builder

- Exact light-mode values, spacing and breakpoints, within the tokens above.
- Page file names and URL slugs (short and lowercase; once the site is live, existing URLs shouldn't change).
- How the home template is wired: a `template:` front-matter override or a Material `home` block override,
  whichever the chosen tool supports.
- Wording, within the copy rules: plain language, sentence case, active voice, no hype.
- Whether a feature clip is GIF only, or video with a GIF fallback, per the size budget.
- Logo SVG details (the amber note mark from the mockup is the starting point) and the favicon.

## Owner decisions recorded

- Host at `arif-banai.github.io/MusicBot` (2026-11-16).
- Audiences: new self-hosters and people coming from the original JMusicBot (2026-11-16).
- Site outline as proposed (2026-11-16).
- Approach A: MkDocs-style Markdown site (2026-11-16).
- Visual direction A, "On air" (2026-11-16).
- Captures recorded by Claude through Chrome, with each clip reviewed by the owner (2026-11-16).
- YouTube-related settings: each documented as a symptom paired with its setting, never how it works (2026-11-16).
- The owner's own Discord account appears in feature captures (2026-11-16).
