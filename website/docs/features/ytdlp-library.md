---
description: Download songs with your own yt-dlp and play them from disk.
---

# yt-dlp library

The yt-dlp library is an optional feature for server owners. When a YouTube song won't play, the bot can download
it with your own copy of [yt-dlp](https://github.com/yt-dlp/yt-dlp), save it to a library folder, and play it from
disk. Saved songs keep working, and the bot can offer them when someone asks for the same song again.

<figure class="oa-media oa-media--pending" markdown>
GIF: the Download button on now-playing, then the song in /library panel
</figure>

## What you need

- **yt-dlp**, installed by you. The bot never installs or updates it, so update it yourself now and then
  (`yt-dlp -U`).
- **A JavaScript runtime** such as [Deno](https://deno.com/) on your `PATH`. yt-dlp needs it to download from
  YouTube.
- On Linux, a UTF-8 locale (for example `LANG=C.UTF-8`), so files with accented or non-Latin names open. The bot
  warns at startup if it's missing.
- ffmpeg isn't needed.

The Docker image doesn't include yt-dlp or Deno. Mount them into the container (for example the standalone
`yt-dlp_linux` build), put Deno on the container's `PATH`, and set `path` to where yt-dlp is inside the container.

## Turn it on

Set `ytdlp.path` in `config.txt` to the yt-dlp program and restart the bot:

```hocon
ytdlp {
  path = "/usr/local/bin/yt-dlp"
  mode = "prompt"
}
```

On Windows, use forward slashes or doubled backslashes, and point at `yt-dlp.exe` itself, not a `.cmd` or `.bat`
file:

```hocon
ytdlp {
  path = "C:/tools/yt-dlp.exe"    # or "C:\\tools\\yt-dlp.exe"
}
```

At startup the bot runs `yt-dlp --version`. If that fails, the feature stays off until the next restart, and the
log says why. Songs already in the library still play.

## Prompt or always

- **`prompt`** (the default): when a YouTube song won't play, the bot pauses on it and posts two buttons:
  **Download & play**, and **Download all YouTube songs in queue**. The requester or a DJ can press them. If nobody
  does within `promptTimeoutSeconds`, the song is skipped. Live streams never get a prompt.
- **`always`**: every YouTube song is downloaded first and played from disk, with no buttons.

## The Download button

With `downloadButton` on, the [now-playing panel](now-playing.md) shows a ⬇ **Download** button that saves the
current song to the library without interrupting it. It's for DJs, and it works for any site yt-dlp supports, not
only YouTube.

## Similar songs and Switch

When someone queues a song, or it starts playing, the bot may say it looks like a song already in your library,
with two buttons:

- **Switch to library copy** plays the saved copy instead. On a playing song it picks up at the same point in the
  music when it can (`matching.alignSwitch`). The bot remembers the link, so that upload plays from the library next
  time.
- **Not the same** tells the bot they're different songs; it won't suggest that pair again.

The requester or a DJ can press them. `/library unlink` removes a remembered link for the song that's playing.

## Managing the library

`/library panel` opens a private panel for DJs with the library's size and buttons to:

- see the current song's library details, and unlink it,
- search the library and play, queue or remove a song,
- rescan the folder.

Removing can keep the file (a rescan brings it back) or delete it. Every downloaded song is also listed in a
playlist, named by `libraryPlaylist`, that you can play like any other.

### Adding songs you already have

Copy audio files into the library folder (`libraryFolder`) and restart the bot. It reads each new file's details
from a yt-dlp `.info.json` file with the same name if there is one, otherwise from a YouTube ID in brackets at the
end of the filename, otherwise from the file's tags. Files are never moved or renamed.

## Who can use it

- Turning it on: the bot owner, in `config.txt`.
- Download & play prompts, Switch and Not the same: the requester or a DJ.
- The Download button, `/library panel` and `/library unlink`: [DJs](dj-role.md).

## Settings

All under `ytdlp` in [config.txt](../reference/config.md#ytdlp):

| Setting | Default | What it does |
|---|---|---|
| `path` | `""` | Path to yt-dlp. Empty turns the feature off. |
| `mode` | `"prompt"` | `prompt` or `always`. |
| `libraryFolder` | `"ytdlp-library"` | Folder for downloaded songs. |
| `libraryPlaylist` | `"ytdlp-library"` | Playlist that lists every downloaded song. |
| `delaySeconds` | `5` | Seconds between downloads. |
| `timeoutSeconds` | `300` | Seconds before a download is cancelled. |
| `extraArgs` | `[]` | Extra yt-dlp arguments, after the bot's own. |
| `promptTimeoutSeconds` | `120` | Seconds to wait for a prompt button before skipping. |
| `downloadButton` | `true` | Show the Download button to DJs. |
| `matching.enabled` | `true` | Suggest similar songs from the library. |
| `matching.durationToleranceSeconds` | `10` | How different two songs' lengths can be, 0 to 60 seconds. |
| `matching.minScore` | `0.85` | How alike titles must be, 0.0 to 1.0. |
| `matching.alignSwitch` | `true` | Switch mid-song at the matching point instead of the start. |

Next: [DJ role and permissions](dj-role.md).
