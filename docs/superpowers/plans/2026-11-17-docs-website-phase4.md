# Docs Website, Phase 4 (launch) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Task 6 has owner-only steps; stop and ask at each one.

**Goal:** Put the docs site live at `https://arif-banai.github.io/MusicBot/`, with every repo pointer moved off jmusicbot.com, and the four checkpoint-3 follow-ups done.

**Architecture:**
- Tasks 1–5 are ordinary commits on `feature/docs-website` (draft PR #96), each with pytest checks in `website/tests/`.
- Task 6 is the launch itself:
  - the owner enables Pages,
  - PR #96 merges to `master`,
  - `.github/workflows/docs.yml` deploys,
  - we check the live URL and set the repo homepage.

**Tech Stack:**
- Zensical 0.1.1 and pytest (existing `website/` toolchain).
- Headless Edge for screenshots.
- `gh` CLI.

**Spec:** `docs/superpowers/specs/2026-11-16-docs-website-design.md` ("Constraints", "Changes outside `website/`", "Phases and checkpoints" item 4)

**Prior plans:** phases 1–3 (done; head `7e71dc4`). Capture tools are in `../docs-website-capture-kit/`.

## Global Constraints

- The site documents the latest published release, **0.8.0**. The release asset is `JMusicBot-0.8.0.jar`. The build's `-All.jar` name never reaches users.
- Owner decisions (2026-11-17):
  - Go live now, on 0.8.0, with no SoundCloud note.
  - Hide the GUI screenshot slot until the follow-up GUI work.
  - Trim the README to an overview.
  - Link-preview card is option B: the headline plus the now-playing panel.
  - Keep the replica's progress bar.
  - Hero fix is option B: contain the backdrop.
- All site links and assets are relative. `og:image` and README links are absolute, starting with `https://arif-banai.github.io/MusicBot/`.
- YouTube copy rule: symptom → setting only. Don't carry over the README's "bypass regional restrictions" wording.
- Java code and `pom.xml` are not touched. `pom.xml:12,25` (`<url>`) and `AudioHandler.java:915` (`jmusicbot.com/youtube-oauth2`) still point at the old site. Report them as a follow-up for `release/0.8.1`.
- Branch names must match `feature/ fix/ chore/ deps/ release/`.
- Owner-only actions:
  - enabling Pages,
  - marking PR #96 ready and merging it,
  - `gh repo edit --homepage`.

  Claude may run the `gh` command only after the owner says yes in chat.

## Review Focus

1. **A README or issue-template link to a site page that 404s** (wrong slug, missing trailing slash). Expected: every `arif-banai.github.io/MusicBot/...` link resolves to a built page. → `test_repo_site_links_resolve` (Task 4).
2. **Link previews (Discord, Twitter) show nothing.** Cause: a relative or SVG `og:image`, or a PNG that isn't 1200×630. Expected: an absolute PNG of the right size. → `test_og_image_*` (Task 3).
3. **The hero at exactly 900/901 px and 1320/1321 px.** Expected: the mobile layout is unchanged at ≤900 px, and the wide layout is pixel-identical at ≥1321 px. → screenshot check in Task 2, Step 4.
4. **The home GUI row without an image on a phone (360 px).** Expected: the text reads as a normal section, with no empty column or dashed box. → `test_gui_row_is_text_only` (Task 1), plus a 360 px screenshot.
5. **First deploy fails on `master`.** Causes: Pages not enabled, or the `github-pages` environment blocks `master`. Expected: the owner enables Pages *before* the merge; the deploy job goes green and the URL returns 200. → Task 6, Steps 1 and 4.

---

### Task 1: Correct the jar name and hide the GUI slot

**Files:**
- Modify:
  - `website/docs/setup/first-run.md`
  - `website/docs/setup/service.md`
  - `website/docs/reference/performance.md`
  - `website/docs/reference/proxy.md`
  - `website/docs/features/gui.md`
  - `website/docs/index.md`
  - `website/docs/stylesheets/home.css`
- Test:
  - `website/tests/test_content.py` (add)
  - `website/tests/test_home.py` (add)
  - `website/tests/test_media.py` (remove the xfail)

**Interfaces:**
- Produces: the CSS class `oa-feature--solo`, a single-column feature row.

- [ ] **Step 1: Write the failing tests**

```python
# test_content.py
def test_jar_name_matches_release_asset():
    bad = [(p.name, l) for p in (WEB / "docs").rglob("*.md") for l in p.read_text(encoding="utf-8").splitlines()
           if "-All.jar" in l]
    assert bad == []
    assert "JMusicBot-0.8.0.jar" in (WEB / "docs/setup/first-run.md").read_text(encoding="utf-8")

# test_home.py
def test_gui_row_is_text_only(site_dir):
    last = soup(site_dir / "index.html").select(".oa-feature")[-1]
    assert "oa-feature--solo" in last["class"] and last.select("figure") == []
    assert soup(site_dir / "features/gui/index.html").select(".md-content figure") == []
    assert re.search(r"\.oa-feature\.oa-feature--solo\s*\{[^}]*grid-template-columns:\s*1fr\s*;",
                     (WEB / "docs/stylesheets/home.css").read_text(encoding="utf-8"))
```

`test_content.py` has no `WEB` yet. Add `WEB = Path(__file__).resolve().parents[1]` below its imports, as `test_home.py` and `test_theme.py` do. `test_repo_links.py` (Task 4) also needs it, plus `REPO = WEB.parent`.

Remove the `@pytest.mark.xfail` line above `test_no_pending_media`.

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `.venv/Scripts/python -m pytest -q tests/test_content.py tests/test_home.py tests/test_media.py`

Expected: these three fail, and nothing else:
- `test_jar_name_matches_release_asset` (8 lines),
- `test_gui_row_is_text_only`,
- `test_no_pending_media` (2 placeholders).

- [ ] **Step 3: Make the changes**
  - Replace `JMusicBot-0.8.0-All.jar` with `JMusicBot-0.8.0.jar` in all four pages.
  - Delete the pending `<figure>` from `gui.md` and `index.md`.
  - Add `oa-feature--solo` to the GUI row's classes and keep `oa-feature--flip`, so `test_home_has_six_feature_rows_alternating` still holds.
  - Add the rule `.md-typeset .oa-feature.oa-feature--solo { grid-template-columns: 1fr; max-width: 38rem; }` to `home.css`. Put it **after** the `@media (min-width: 901px)` flip block so it wins.
  - Add a comment to `gui.md` naming the screenshot slot, so the follow-up knows where it goes: `<!-- screenshot: desktop app, Performance tab (deferred, see phase 4 plan) -->`

- [ ] **Step 4: Run all tests and take a 360 px screenshot**

Run: `.venv/Scripts/python -m pytest -q`

Expected: all pass, with no xfail left.

Then take a headless Edge screenshot of `/MusicBot/` at 360×2400. The GUI row should read as plain text.

- [ ] **Step 5: Commit**

Message: `docs(site): use the release jar name; hide the GUI slot until its screenshot exists`

---

### Task 2: Contain the hero backdrop between 901 and 1320 px

**Files:**
- Modify: `website/docs/stylesheets/home.css`
- Test: `website/tests/test_home.py`

- [ ] **Step 1: Write the failing test**

```python
def test_hero_backdrop_contained_on_mid_widths():
    css = (WEB / "docs/stylesheets/home.css").read_text(encoding="utf-8")
    block = re.search(r"@media \(min-width: 901px\) and \(max-width: 1320px\)\s*\{(.*?)\n\}", css, re.S)
    assert block, "mid-width hero block missing"
    assert re.search(r"\.oa-stage::before\s*\{\s*inset:\s*0 0 0 1\.2rem;", block.group(1))
```

- [ ] **Step 2: Run the test and confirm it fails**

Run: `.venv/Scripts/python -m pytest -q tests/test_home.py::test_hero_backdrop_contained_on_mid_widths`

Expected: FAIL with "mid-width hero block missing".

- [ ] **Step 3: Add the block (the approved mockup's values)**

```css
@media (min-width: 901px) and (max-width: 1320px) {
  .oa-stage::before { inset: 0 0 0 1.2rem; }
  .oa-hero__inner { gap: 2rem; padding-left: 1.6rem; padding-right: 1.6rem; }
  .oa-np__body { padding-left: 0; }
}
```

Place it after the existing `@media (min-width: 901px) and (max-width: 1100px)` rule. That rule's `.oa-np__body` setting is now covered by the new block, so delete it.

- [ ] **Step 4: Run all tests and compare screenshots**

Run: `.venv/Scripts/python -m pytest -q`

Expected: all pass.

Take headless Edge screenshots of `/MusicBot/` at 900, 1150, 1320, 1321 and 1440 px (height 760). Check:
- At 1150 and 1320 px, the backdrop sits inside the window.
- At 1321 and 1440 px, the page matches the `hero-current-1440.png` mockup.
- At 900 px, the mobile layout is unchanged.

- [ ] **Step 5: Commit**

Message: `fix(site): keep the hero backdrop inside the page at mid widths`

---

### Task 3: Link-preview card (og:image)

**Files:**
- Create:
  - `website/docs/assets/images/og-card.png` (1200×630)
  - `website/tools/og-card.css`
- Modify:
  - `website/overrides/main.html:11`
  - `website/README.md` (add a "Link-preview card" section)
- Test: `website/tests/test_theme.py`

**Interfaces:**
- Produces: the meta tags `og:image`, `og:image:width`, `og:image:height` and `og:image:alt`, plus `twitter:card` = `summary_large_image`.

- [ ] **Step 1: Write the failing tests**

```python
def _png_size(p):
    b = p.read_bytes()
    assert b[:8] == b"\x89PNG\r\n\x1a\n"
    return int.from_bytes(b[16:20], "big"), int.from_bytes(b[20:24], "big")

def test_og_image_is_absolute_png(site_dir):
    want = "https://arif-banai.github.io/MusicBot/assets/images/og-card.png"
    for p in html_pages(site_dir):
        s = soup(p)
        assert s.select_one('meta[property="og:image"]')["content"] == want, p
        assert s.select_one('meta[name="twitter:card"]')["content"] == "summary_large_image", p

def test_og_card_size():
    assert _png_size(WEB / "docs/assets/images/og-card.png") == (1200, 630)
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `.venv/Scripts/python -m pytest -q tests/test_theme.py -k og`

Expected: both FAIL. One gets the favicon `.svg` instead; the other finds no PNG.

- [ ] **Step 3: Make the card and wire up the tags**
  - **Card CSS:** `website/tools/og-card.css` is the approved option B's override CSS (copy the `card-b` style string from `../docs-website-capture-kit/mock/gen.py`; the approved render is `mock/card-b.png` there). It hides the header, main, footer, `.oa-paths`, the caption and the lede. It lays the hero out as two columns, 60px/56px padding, with a 64px title. It adds the site URL after `.oa-hero__copy`.
  - **Render:** apply that CSS to the built home page and screenshot it with headless Edge at 1200×630, scale 1. Save the result as `og-card.png`.
  - **Document:** write the exact command in `website/README.md`, so the card can be re-rendered when the hero changes.
  - **`main.html`:** point `og:image` at `{{ config.site_url }}assets/images/og-card.png`. Add:
    - `og:image:width` 1200 and `og:image:height` 630,
    - `og:image:alt` "JMusicBot: the music bot you run yourself, with an example now-playing panel",
    - `<meta name="twitter:card" content="summary_large_image">`.
  - The PNG lives in `assets/images/`, not `assets/media/`, so `test_every_media_file_is_used` doesn't apply to it.

- [ ] **Step 4: Run all tests**

Run: `.venv/Scripts/python -m pytest -q`

Expected: all pass.

- [ ] **Step 5: Commit**

Message: `feat(site): PNG link-preview card`

---

### Task 4: README overview, repo links, CNAME

**Files:**
- Modify:
  - `README.md`
  - `.github/ISSUE_TEMPLATE/bug-report.yml:52`
  - `.github/ISSUE_TEMPLATE/feature-request.yml:45-50`
  - `.github/workflows/make-release.yml:151-152`
  - `Dockerfile:64`
  - `.github/workflows/docs.yml` (add `README.md` and `.github/ISSUE_TEMPLATE/**` to both `paths` lists)
- Delete: `CNAME`
- Test: `website/tests/test_repo_links.py` (new)

**Interfaces:**
- Consumes: `REPO = WEB.parent`, `site_dir`.
- Produces: `SITE = "https://arif-banai.github.io/MusicBot/"`.

- [ ] **Step 1: Write the failing tests**

```python
OUTSIDE = ["README.md", "Dockerfile", ".github/ISSUE_TEMPLATE/bug-report.yml",
           ".github/ISSUE_TEMPLATE/feature-request.yml", ".github/workflows/make-release.yml"]
OLD = re.compile(r"https?://(www\.)?jmusicbot\.com")
LINK = re.compile(r"https://arif-banai\.github\.io/MusicBot/([^\s)\"'>]*)")

def text(f): return (REPO / f).read_text(encoding="utf-8")

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
```

- [ ] **Step 2: Run the tests and confirm they fail**

Run: `.venv/Scripts/python -m pytest -q tests/test_repo_links.py`

Expected: every test FAILs except `test_repo_site_links_resolve`, which passes vacuously because no links exist yet.

- [ ] **Step 3: Rewrite the README as an overview**

Keep these, in this order:
1. The logo, fork note, badges and one-line description.
2. The "Check your Java version and other requirements" block, with its wording kept: Java 25, glibc ≥ 2.38, and the Message Content Intent.
3. The Setup banner image, now linking to `SITE + "setup/"`.
4. The Features list.
5. The Sources and formats lists.
6. A new **"Quick start"**, five lines or fewer: download `JMusicBot-<version>.jar` from Releases, run `java -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED -jar JMusicBot-0.8.0.jar`, and the first run asks for the token and owner ID. Then link the full Setup guide and "Run with Docker".
7. A **"Documentation"** list with one line and link per site page from `test_readme_points_at_core_pages`.
8. "Development Workflow", "Questions/Suggestions/Bug Reports" and "Editing", all unchanged.

Delete everything else: the long Running, Linux, Docker, Performance, Proxy and yt-dlp sections. Their content lives on the site, and the deletion also removes the wrong "generates a default `config.txt`" Docker claim.

- [ ] **Step 4: Update the other files and delete CNAME**
  - **Issue templates:** documentation links go to `SITE`. In the bug report, the troubleshooting link goes to `SITE + "help/troubleshooting/"`. Delete the feature-request checkbox about "list of things that will not be added", since that page was dropped at checkpoint 2.
  - **`make-release.yml` body:** `SITE + "setup/"` and `SITE + "reference/config/"`.
  - **`Dockerfile` label:** `org.opencontainers.image.url="https://arif-banai.github.io/MusicBot/"`.
  - **CNAME:** `git rm CNAME`.

- [ ] **Step 5: Run all tests and validate the YAML**

Run: `.venv/Scripts/python -m pytest -q`

Expected: all pass.

Then: `python -c "import yaml,sys;[yaml.safe_load(open(f)) for f in sys.argv[1:]]" .github/ISSUE_TEMPLATE/*.yml .github/workflows/make-release.yml .github/workflows/docs.yml`

Expected: no output.

- [ ] **Step 6: Commit, push, and show the owner the README**

Message: `docs: README overview pointing at the docs site; move repo links off jmusicbot.com; drop CNAME`

Push, and check that `gh pr checks 96 --repo arif-banai/MusicBot` shows "Build and test docs" passing. The spec says the trim is shown to the owner in the PR, so give them the README diff link and wait for their OK before Task 5.

---

### Task 5: Final review gate

- [ ] **Step 1: Review the whole branch.** Request a code review of `adeec27..HEAD` covering the Review Focus list above. Fix any Important findings and commit.
- [ ] **Step 2: Mark the PR ready (owner OK).** Update the PR #96 description: what launches, the deferred GUI shot, and the `pom.xml`/`AudioHandler` follow-up. Ask the owner to mark it ready for review, or get their OK to run `gh pr ready 96 --repo arif-banai/MusicBot`.

---

### Task 6: Launch (owner-gated)

- [ ] **Step 1: The owner enables Pages.** Settings → Pages → Source: **GitHub Actions**.

  Verify: `gh api repos/arif-banai/MusicBot/pages --jq .build_type` prints `workflow`. The 404 from the spec's Constraints must be gone.

  Also check: `gh api repos/arif-banai/MusicBot/environments/github-pages --jq '.deployment_branch_policy'`. Either it allows `master`, or it's null.

- [ ] **Step 2: The owner merges PR #96 into `master`.** Use their usual merge method; ask which, don't assume.

- [ ] **Step 3: Watch the deploy.**

  Run: `gh run watch --repo arif-banai/MusicBot $(gh run list --repo arif-banai/MusicBot --workflow docs.yml --branch master --limit 1 --json databaseId --jq '.[0].databaseId')`

  Expected: the "Build and test docs" and "Deploy to GitHub Pages" jobs both succeed.

- [ ] **Step 4: Check the live site.**

  Every one of these returns 200:

  ```bash
  for p in "" setup/ setup/bot-token/ features/now-playing/ reference/config/ help/troubleshooting/ assets/images/og-card.png assets/media/slash-play.webp; do
    curl -s -o /dev/null -w "%{http_code} $p\n" "https://arif-banai.github.io/MusicBot/$p"; done
  ```

  Then open the live home page in the browser at desktop width and at 360 px, and check that the clips animate.

- [ ] **Step 5: Set the repo homepage (owner OK in chat first).**

  Run: `gh repo edit arif-banai/MusicBot --homepage https://arif-banai.github.io/MusicBot/`

  Verify: `gh repo view arif-banai/MusicBot --json homepageUrl` returns the new URL.

- [ ] **Step 6: Report.** Give the owner:
  - the live URL;
  - the deferred GUI screenshot: its slot comment is in `gui.md`, and the capture kit is in `../docs-website-capture-kit/`;
  - the follow-up issue text for `pom.xml` `<url>` and `AudioHandler.java:915` (target `release/0.8.1`);
  - the throwaway "JMusicBot Docs Demo" app, which still needs deleting or a token reset;
  - the possible bot bug where the now-playing footer kept saying "playing from history" after a playlist's Play Now.
