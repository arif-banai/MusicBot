# JMusicBot docs website

Source for <https://arif-banai.github.io/MusicBot/>. The pages are Markdown files in `docs/`. The theme lives in
`docs/stylesheets/` and `overrides/`.

## Preview locally

```bash
cd website
python -m venv .venv
.venv/Scripts/python -m pip install -r requirements.txt   # Linux/macOS: .venv/bin/python
.venv/Scripts/python -m zensical serve
```

Open the address it prints. The site lives under `/MusicBot/`, as it does on GitHub Pages.

## Test

```bash
.venv/Scripts/python -m pytest -q
```

The tests build the site in strict mode, then check the output:

- Every link, image and stylesheet path resolves.
- No path starts with `/`, which would break under the `/MusicBot/` subpath.
- Images have alt text.
- Theme colors pass contrast.
- Copy rules hold.

CI runs the same command (`.github/workflows/docs.yml`).

## Why Zensical

The site is built with [Zensical](https://zensical.org/), the successor to Material for MkDocs, which has been in
maintenance mode since November 2025. The config stays in `mkdocs.yml` and uses only features both tools support.
If Zensical can't build something we need, the fallback is:

```bash
pip install mkdocs-material==9.8.1
mkdocs build
```

Don't use these settings, which Zensical doesn't support yet: `hooks`, `exclude_docs`, `draft_docs`, `not_in_nav`,
`remote_branch`, `remote_name`.

## Screenshots and GIFs

Until a capture exists, a page shows a placeholder:

```html
<figure class="oa-media oa-media--pending" markdown>
Screenshot: Developer Portal, New Application dialog
</figure>
```

Swap it for the real image or video, keeping the `oa-media` class so it gets the Discord frame:

```html
<figure class="oa-media" markdown>
![What the clip shows](../assets/media/new-application.png)
</figure>
```

Keep each animation under about 1.5 MB and give every image alt text that says what it shows.

## Link-preview card

`docs/assets/images/og-card.png` is the image Discord, Slack and other sites show when someone shares a link to the
site. It's the home page's hero, laid out at 1200x630 by `tools/og-card.css`. Re-render it after changing the hero:
with `zensical serve -a localhost:8765` running, run

```
python tools/render_og_card.py "C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
```

Any Chromium-based browser works in place of Edge.
