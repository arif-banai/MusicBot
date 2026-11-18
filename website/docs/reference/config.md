---
description: Every setting in config.txt, its default and what it does.
---

# config.txt

`config.txt` sits next to the bot's jar (or in the folder you mount into Docker). The bot reads it at startup, so
restart the bot after you change it. Only `discord.token` and `discord.owner` are required; everything else has a
default.

The file uses nested blocks. A setting written as `nowPlaying.showButtons` below lives here in the file:

```hocon
nowPlaying {
  showButtons = true
}
```

Text values go in double quotes. On Windows, write paths with forward slashes (`"C:/tools/yt-dlp.exe"`) or doubled
backslashes, because a single backslash starts an escape sequence.

!!! note "Don't edit `meta.configVersion`"
    The bot uses it to update older config files automatically. If you're coming from the original JMusicBot, see
    [Coming from JMusicBot](../migrating.md).

## discord

| Setting | Default | What it does |
|---|---|---|
| `discord.token` | `"BOT_TOKEN_HERE"` | Your bot's token. See [Get a bot token](../setup/bot-token.md). Required. |
| `discord.owner` | `0` | Your Discord user ID, which makes you the bot owner. See [Find your user ID](../setup/user-id.md). Required. |

## playback

| Setting | Default | What it does |
|---|---|---|
| `playback.maxTrackSeconds` | `0` | Longest song allowed, in seconds. `0` means no limit. |
| `playback.maxYouTubePlaylistPages` | `10` | How many pages of a YouTube playlist to load, 100 songs each. |
| `playback.maxHistorySize` | `40` | How many played songs to remember for `history` and the Prev button. `0` turns history off. |
| `playback.skipRatio` | `0.55` | Share of listeners who must vote to skip a song. Servers can override it with `setskip`. |
| `playback.youtube.useOAuth` | `false` | If YouTube songs fail with "Sign in to confirm you're not a bot" or "Video unavailable", set this to `true`. See [below](#signing-in-to-youtube). |
| `playback.audioSources.youtube` | `true` | Allow YouTube. |
| `playback.audioSources.soundcloud` | `true` | Allow SoundCloud. |
| `playback.audioSources.bandcamp` | `true` | Allow Bandcamp. |
| `playback.audioSources.vimeo` | `true` | Allow Vimeo. |
| `playback.audioSources.twitch` | `true` | Allow Twitch streams. |
| `playback.audioSources.beam` | `true` | Allow Beam streams. |
| `playback.audioSources.getyarn` | `true` | Allow getyarn.io clips. |
| `playback.audioSources.nico` | `true` | Allow NicoNico. |
| `playback.audioSources.http` | `true` | Allow direct links to audio files and radio streams. |
| `playback.audioSources.local` | `true` | Allow files on the bot's computer. |
| `playback.transforms` | `{}` | Advanced: rewrites what people ask to play, for allow lists, block lists or custom routing. Leave it empty unless you've read the code for it. |

### Signing in to YouTube

When `playback.youtube.useOAuth = true`, the bot prints a code on its next start (and messages it to the owner).
Open `https://www.google.com/device`, sign in with a separate Google account rather than your main one, and enter
the code. You only do this once; the bot remembers the sign-in.

## commands

| Setting | Default | What it does |
|---|---|---|
| `commands.prefix` | `"@mention"` | The prefix for text commands. `@mention` means you start with a mention of the bot: `@JMusicBot play`. |
| `commands.altPrefix` | `"NONE"` | A second prefix that also works, such as `"!!"`. `NONE` turns it off. |
| `commands.help` | `"help"` | The word that shows the command list, used after the prefix. |
| `commands.clearChannel.deleteLimit` | `50` | Most messages `clearchannel` deletes at once. `0` deletes every eligible message. |
| `commands.clearChannel.ageDays` | `14` | Only messages newer than this many days are deleted. `0` turns the age limit off. |

`commands.aliases` holds extra names for prefix commands, one list per command, for example `play = [ "p" ]`. The
defaults are listed on the [Commands](commands.md) page.

## presence

| Setting | Default | What it does |
|---|---|---|
| `presence.game` | `"DEFAULT"` | The bot's status text. `DEFAULT` uses the built-in text, `NONE` shows nothing, anything else shows that text. |
| `presence.status` | `"ONLINE"` | `ONLINE`, `IDLE`, `DND` or `INVISIBLE`. |
| `presence.songInStatus` | `false` | Shows the current song as the bot's status. Only works well if the bot plays in one server. |

## ui

| Setting | Default | What it does |
|---|---|---|
| `ui.emojis.success` | `"🎶"` | Emoji in front of success messages. |
| `ui.emojis.warning` | `"💡"` | Emoji in front of warnings. |
| `ui.emojis.error` | `"🚫"` | Emoji in front of errors. |
| `ui.emojis.loading` | `"⌚"` | Emoji shown while a song loads. |
| `ui.emojis.searching` | `"🔎"` | Emoji shown while searching. |

## gui

| Setting | Default | What it does |
|---|---|---|
| `gui.enabled` | `true` | Opens the [desktop app](../features/gui.md) window on computers with a screen. `-Dnogui=true` on the command line also turns it off. |
| `gui.theme` | `"dark"` | Window theme: `light`, `dark`, `darcula` or `intellij`. |
| `gui.fontSize` | `12` | Text size in the window, from 8 to 24. |

## nowPlaying

| Setting | Default | What it does |
|---|---|---|
| `nowPlaying.images` | `false` | Shows the song's thumbnail. Only in the full layout. |
| `nowPlaying.minimalMessage` | `false` | Uses the compact layout instead of the full one. Servers can override it with `nplayout`. |
| `nowPlaying.showButtons` | `true` | Adds the control buttons under the song. Servers can override it with `npbuttons`. |
| `nowPlaying.showProgressBar` | `false` | Shows a progress bar and refreshes the message every 10 seconds. |

See [Now-playing panel](../features/now-playing.md) for what each option looks like.

## voice

| Setting | Default | What it does |
|---|---|---|
| `voice.stayInChannel` | `false` | Stays in the voice channel after the queue ends, which uses some bandwidth. |
| `voice.aloneTimeUntilStopSeconds` | `10` | Seconds the bot waits alone in a channel before it stops and leaves. `0` turns this off. |

## paths

| Setting | Default | What it does |
|---|---|---|
| `paths.playlistsFolder` | `"Playlists"` | Folder that holds playlist files, relative to the bot's folder or a full path. |

## updates

| Setting | Default | What it does |
|---|---|---|
| `updates.alerts` | `true` | Messages the owner when a new version is out. |

## logging

| Setting | Default | What it does |
|---|---|---|
| `logging.level` | `"info"` | How much the bot logs: `off`, `error`, `warn`, `info`, `debug`, `trace` or `all`. Use `info` unless you're chasing a problem. |

## performance

| Setting | Default | What it does |
|---|---|---|
| `performance.nasBufferMs` | `800` | Audio kept ready to send, in milliseconds. Covers pauses in Java up to this long. |
| `performance.frameBufferMs` | `2000` | Decoded audio kept ahead of playback, in milliseconds. |

Higher values protect against stutter but add a little delay. See [Performance tuning](performance.md).

## proxy

| Setting | Default | What it does |
|---|---|---|
| `proxy.host` | `""` | Proxy server address. Leave it empty for no proxy. |
| `proxy.port` | `0` | Proxy server port. |
| `proxy.lavaplayer` | `false` | Sends music requests (YouTube, SoundCloud and so on) through the proxy. |
| `proxy.jda` | `false` | Sends Discord traffic through the proxy. |
| `proxy.github` | `false` | Sends the update check through the proxy. |

See [Proxy](proxy.md) for examples.

## dangerous

| Setting | Default | What it does |
|---|---|---|
| `dangerous.eval` | `false` | Turns on the owner-only `eval` command, which runs any code. Leave it off. |
| `dangerous.evalEngine` | `"Nashorn"` | The script engine `eval` uses. |

## ytdlp

These settings only matter if you use the [yt-dlp library](../features/ytdlp-library.md).

| Setting | Default | What it does |
|---|---|---|
| `ytdlp.path` | `""` | Path to your yt-dlp program. Empty turns the feature off. Point it at the program itself (such as `yt-dlp.exe`), not a `.cmd` or `.bat` file. |
| `ytdlp.mode` | `"prompt"` | `prompt`: when a YouTube song won't play, the bot offers buttons to download it. `always`: every YouTube song is downloaded first. |
| `ytdlp.libraryFolder` | `"ytdlp-library"` | Folder for downloaded songs. |
| `ytdlp.libraryPlaylist` | `"ytdlp-library"` | Name of the playlist that lists every downloaded song. |
| `ytdlp.delaySeconds` | `5` | Seconds to wait between downloads. |
| `ytdlp.timeoutSeconds` | `300` | Seconds before a download is cancelled. |
| `ytdlp.extraArgs` | `[]` | Extra arguments added after the bot's own when it runs yt-dlp. |
| `ytdlp.promptTimeoutSeconds` | `120` | In `prompt` mode, seconds to wait for a button press before skipping the song. |
| `ytdlp.downloadButton` | `true` | Adds a Download button (DJs only) to now-playing that saves the current song. |
| `ytdlp.matching.enabled` | `true` | Offers a matching song from the library when a song is queued or starts. |
| `ytdlp.matching.durationToleranceSeconds` | `10` | How far apart two songs' lengths can be and still match, from 0 to 60. Widens to 10% for long songs. |
| `ytdlp.matching.minScore` | `0.85` | How alike titles and artists must be to match, from 0.0 to 1.0. Higher is stricter. |
| `ytdlp.matching.alignSwitch` | `true` | When you switch to the library copy, picks up at the same point in the song instead of the start. |
