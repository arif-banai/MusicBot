# Docs Website, Phase 3 (screenshots and GIFs) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. This phase is interactive: the owner approves sites before capture and every clip before it is committed. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace every `.oa-media--pending` placeholder with a real screenshot or GIF, captured by Claude through Claude in Chrome. The owner approves each capture before it's committed. The phase ends at checkpoint 3 (media review).

**Architecture:**
- Captures land in `website/docs/assets/media/`.
- Each placeholder becomes `<figure class="oa-media" markdown>` with an `![alt](...)` image (or a `<video>` with a GIF fallback) and a short `<figcaption>`.
- A pytest check enforces that no placeholders remain, that every media file exists and stays within budget, and that every image has alt text.

**Tech Stack:**
- Claude in Chrome: `gif_creator`, `computer` screenshots, `resize_window`.
- Discord web: `discord.com/app`.
- The Discord Developer Portal.
- The local test bot.
- Optionally ffmpeg, if the owner has it, to turn a GIF into MP4/WebM. If not, keep GIFs and stay within the budget.

**Spec:** `docs/superpowers/specs/2026-11-16-docs-website-design.md` ("Media plan")

**Prior plans:** phase 1 and phase 2 (done; draft PR #96).

## Global Constraints

- **Owner approval gates (stop and ask, never assume):**
  1. Before the first capture, the owner approves Claude in Chrome access to `discord.com` and `discord.com/developers`, and names the test server and channel to use.
  2. Every capture is shown to the owner before it's committed. A rejected capture is redone or dropped. A dropped capture removes its placeholder, and the surrounding text must still read correctly.
- **Developer Portal:** use a **throwaway application** created for the docs (e.g. "JMusicBot Docs Demo"). **Never press Reset Token on the real bot's application.** Blur or crop the token, client secret and application ID.
- **Discord framing:**
  - Crop to the message or panel area.
  - No server list, member list, or DMs.
  - The owner's own account may appear (owner decision, 2026-11-16).
  - No other real users' names or avatars.
  - Hide the channel name if it reveals anything private.
- **Bot:** the test bot running the latest published release (0.8.0, or 0.8.1 if it has shipped). Use songs that are safe to show publicly.
- **Budget:**
  - Each animation under about 1.5 MB, 3–8 s long, about 800–1000 px wide.
  - Screenshots are PNG or WebP, at most about 1400 px wide.
  - All media together under 40 MB.
  - These limits are enforced by test.
- **Alt text:** every image says what it shows, in one sentence.
- **YouTube copy rule:** captions say what the user sees, never how YouTube is worked around.
- **Known issue:** issue #95 ("Soundcloud broken"). For the sources GIF, check SoundCloud first. If it fails, use Bandcamp plus a radio stream, and update the placeholder text to match.

## Shot list (16 unique captures; 6 reused on the home page)

| # | File (`assets/media/…`) | Page(s) | What | Kind |
|---|---|---|---|---|
| 1 | `portal-new-app.png` | setup/bot-token | New Application dialog | screenshot |
| 2 | `portal-bot-token.png` | setup/bot-token | Bot tab, Reset Token / Copy, token blurred | screenshot |
| 3 | `portal-intents.png` | setup/bot-token | Privileged Gateway Intents, Message Content on | screenshot |
| 4 | `portal-oauth-url.png` | setup/invite | OAuth2 URL Generator: bot scope plus permissions | screenshot |
| 5 | `discord-dev-mode.png` | setup/user-id | User Settings → Advanced → Developer Mode | screenshot |
| 6 | `discord-copy-id.png` | setup/user-id | Right-click your name → Copy User ID (ID not readable) | screenshot |
| 7 | `np-buttons.gif` | features/now-playing, home | Pause, Skip, Vol + on the panel | GIF |
| 8 | `np-layouts.png` | features/now-playing | Full and minimal layouts side by side | screenshot |
| 9 | `slash-play.gif` | features/slash-commands, home | Typing /play, picking a suggestion, song starts | GIF |
| 10 | `queue-paging.gif` | features/queue-history-playlists | /queue with paging buttons | GIF |
| 11 | `history.gif` | features/queue-history-playlists, home | /history, Play Now, Save as playlist | GIF |
| 12 | `playlists.gif` | features/queue-history-playlists | /playlists, browse, Play Now | GIF |
| 13 | `sources.gif` | features/sources | /play with a non-YouTube link, then a radio stream | GIF |
| 14 | `ytdlp-download.gif` | features/ytdlp-library, home | Download button, then the song in /library panel | GIF |
| 15 | `setdj.png` | features/dj-role, home | /setdj choosing a role, bot confirms | screenshot |
| 16 | `gui-performance.png` | features/gui, home | Desktop app, Performance tab | screenshot |

Phase 2's verify runs confirmed the Portal button names only from code. Re-check each one against the live Portal while capturing shots 1–4, and fix the page text if a label differs.

---

### Task 1: Media checks and capture setup

**Files:**
- Create: `website/tests/test_media.py`
- Create: `website/docs/assets/media/.gitkeep`

**Interfaces:**
- Consumes: `site_dir`, `html_pages`, `soup` (phase 1).
- Produces: `MEDIA = WEB/"docs/assets/media"`, and the budget constants `MAX_ANIM_BYTES = 1_600_000`, `MAX_TOTAL_BYTES = 40_000_000`.

- [ ] **Step 1: Write the tests**

```python
def test_no_pending_media(site_dir):
    left = [(str(p.relative_to(site_dir)), f.get_text(strip=True)) for p in html_pages(site_dir)
            for f in soup(p).select(".oa-media--pending")]
    assert left == []

def test_media_within_budget():
    files = [f for f in MEDIA.rglob("*") if f.is_file() and f.name != ".gitkeep"]
    assert [f.name for f in files if f.suffix in (".gif", ".mp4", ".webm") and f.stat().st_size > MAX_ANIM_BYTES] == []
    assert sum(f.stat().st_size for f in files) <= MAX_TOTAL_BYTES

def test_every_media_file_is_used(site_dir):
    used = {Path(r).name for p in html_pages(site_dir) for r in local_refs(p) if "assets/media/" in r}
    assert sorted(f.name for f in MEDIA.iterdir() if f.is_file() and f.name != ".gitkeep") == sorted(used)
```

Run: `.venv/Scripts/python -m pytest -q tests/test_media.py`
Expected:
- `test_no_pending_media` FAILS, listing the 22 placeholder uses.
- The budget and used-files tests PASS (empty folder).

Commit the tests and mark the pending one `xfail(strict=True, reason="phase 3 in progress")`. Remove the mark in Task 4.

- [ ] **Step 2: Set up capturing (stop: owner approval needed)**

1. Start the test bot.
2. Load the Claude in Chrome tools with `tabs_context_mcp`. If it reports "standalone sidebar mode", ask the owner to open the extension in a normal window.
3. Ask the owner to:
   - approve `discord.com`,
   - name the test server and channel,
   - confirm the throwaway Portal application's name.
4. Set the Chrome window to 1280×900 with `resize_window`.

---

### Task 2: Developer Portal and Discord settings screenshots (shots 1–6)

**Files:**
- Create: `website/docs/assets/media/portal-*.png`, `discord-dev-mode.png`, `discord-copy-id.png`
- Modify: `website/docs/setup/{bot-token,invite,user-id}.md`

For each shot:
1. Navigate.
2. Take a screenshot with `save_to_disk`.
3. Crop or blur any secrets. Use Python and Pillow in the scratchpad venv; nothing gets added to `requirements.txt`.
4. Show the owner.
5. On approval, copy the file into `assets/media/` and replace the placeholder.

The replacement looks like this:

```html
<figure class="oa-media" markdown>
![The New Application dialog in the Discord Developer Portal, with a name typed in](../assets/media/portal-new-app.png)
</figure>
```

Also check the button labels against the page text, and remove the `<!-- verify at capture -->` comment from `bot-token.md`.

- [ ] Shots 1–6 approved and placed
- [ ] Run: `.venv/Scripts/python -m pytest -q`. Expected: all pass except the xfail.
- [ ] Commit: `docs(site): developer portal and discord settings screenshots`

---

### Task 3: Bot feature captures (shots 7–16)

**Files:**
- Create: `website/docs/assets/media/*.gif`, `np-layouts.png`, `setdj.png`, `gui-performance.png`
- Modify: the feature pages and `index.md`

For each GIF:
1. `gif_creator` start.
2. A few still frames.
3. The interaction.
4. A few still frames.
5. Stop and export.
6. Check the size. If it's over budget, trim frames or lower the width. If the owner has ffmpeg, an MP4/WebM `<video autoplay loop muted playsinline>` with the GIF inside as a fallback is allowed.
7. Show the owner.
8. Place it once approved.

Other details:
- **Shot 8:** switch layouts with `/nplayout full`, then `minimal`. Combine the two screenshots side by side with Pillow.
- **Shot 14:** needs `ytdlp.path` set on the test bot; ask the owner if it isn't.
- **Shot 16:** a desktop screenshot of the bot's window. Ask the owner to take it, or use the computer-use tool if it's available and the owner approves.

- [ ] Shots 7–16 approved and placed
- [ ] Run: `.venv/Scripts/python -m pytest -q`. Expected: all pass except the xfail.
- [ ] Commit: `docs(site): feature screenshots and GIFs`

---

### Task 4: No placeholders left, checkpoint 3

- [ ] Remove the `xfail` from `test_no_pending_media`.

  Run: `.venv/Scripts/python -m pytest -q`
  Expected: all pass.

- [ ] Commit and push.

  Expected: `gh pr checks 96 --repo arif-banai/MusicBot` shows "Build and test docs" passing.

- [ ] Checkpoint 3: the owner reviews the site with media in place (local serve plus the PR artifact). Their changes become fix-ups. Then phase 4 (launch) gets its plan:
  - README links and the Docker "generates config" claim,
  - removing `CNAME`,
  - enabling Pages,
  - merging,
  - a PNG `og:image`.
