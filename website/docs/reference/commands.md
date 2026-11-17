---
description: Every JMusicBot command, prefix and slash, grouped by who can use it.
---

# Commands

Every command works two ways:

- **Slash:** type `/` in Discord and pick the command from the list, for example `/play`.
- **Prefix:** mention the bot, then the command, for example `@JMusicBot play never gonna give you up`. You can
  change the prefix with `commands.prefix` in [config.txt](config.md#commands), or per server with `prefix`.

Most commands have both forms. A "—" means that form doesn't exist. Aliases are extra names for the prefix form;
you can change them under `commands.aliases` in `config.txt`.

The groups below are about who can run a command:

- **General** and **Music**: anyone in the server.
- **DJ**: people with the server's DJ role (set it with `setdj`) or the **Manage Server** permission.
- **Admin**: people with **Manage Server**.
- **Owner**: only the bot owner (the `owner` ID in `config.txt`).

Some music commands also need you to be in the bot's voice channel, or a song to be playing. The bot tells you
when that's the case.

## General

| Command | Slash | What it does | Aliases |
|---|---|---|---|
| `settings` | — | Shows the bot's settings for this server. Admins get an interactive panel. `/settings` is in Admin. | `status` |

## Music

| Command | Slash | What it does | Aliases |
|---|---|---|---|
| `play` | `/play` | Plays a song: a title to search for, a link, or an audio file attached to your message. With nothing after it, a DJ can resume a paused song. `play playlist <name>` plays one of the server's playlists. | — |
| `search` | `/search` | Searches YouTube and lets you pick a result. | `ytsearch` |
| `scsearch` | — | Searches SoundCloud and lets you pick a result. | — |
| `nowplaying` | `/nowplaying` | Shows the song that's playing, with the [now-playing panel](../features/now-playing.md). `/np` does the same. | `np`, `current` |
| — | `/np` | Short form of `/nowplaying`. | — |
| `queue` | `/queue` | Shows the queue, a page at a time. | `list` |
| `history` | `/history` | Shows recently played songs, with buttons to play one again or save them as a playlist. | — |
| `playlists` | `/playlists` | Lists the server's playlists. | `pls` |
| `remove` | `/remove` | Removes a song from the queue by its position. `remove all` removes everything you added. | `delete` |
| `shuffle` | `/shuffle` | Shuffles the songs you added. | — |
| `skip` | `/skip` | Votes to skip the current song. The person who queued it skips right away. | `voteskip` |
| `seek` | `/seek` | Jumps to a time in the song, such as `1:30`, `+30` or `1m30s`. | — |
| `lyrics` | — | Not available in this version. The lyrics service it used was removed. | — |

## DJ

| Command | Slash | What it does | Aliases |
|---|---|---|---|
| `forceskip` | `/forceskip` | Skips the current song without a vote. | `modskip` |
| `forceremove` | `/forceremove` | Removes every song a person added. | `forcedelete`, `modremove`, `moddelete`, `modelete` |
| `movetrack` | `/movetrack` | Moves a song to another position in the queue. | `move` |
| `pause` | `/pause` | Pauses the current song. Use `play` with nothing after it, or the Resume button, to carry on. | — |
| `playnext` | `/playnext` | Plays a single song next, ahead of the queue. | — |
| `repeat` | `/repeat` | Repeats the queue (`all`), one song (`single`), or turns repeat `off`. | — |
| `skipto` | `/skipto` | Skips straight to a position in the queue. | `jumpto` |
| `stop` | `/stop` | Stops playback, clears the queue and leaves the voice channel. | `leave` |
| `volume` | `/volume` | Shows the volume, or sets it from 0 to 150. | `vol` |
| `library` | `/library` | Commands for the [yt-dlp library](../features/ytdlp-library.md). Needs yt-dlp set up. | — |
| — | `/library panel` | Opens the library panel to browse, play and manage downloaded songs. | — |
| — | `/library unlink` | Unlinks the current song from the library song it was matched to. The prefix form is `library unlink`. | — |

## Admin

| Command | Slash | What it does | Aliases |
|---|---|---|---|
| — | `/settings` | Opens the interactive settings panel for this server. | — |
| `setdj` | `/setdj` | Sets the DJ role, or `NONE` to clear it. | — |
| `settc` | `/settc` | Limits music commands to one text channel, or `NONE`. | — |
| `setvc` | `/setvc` | Limits the bot to one voice channel, or `NONE`. | — |
| `setskip` | `/setskip` | Sets the percentage of listeners needed to vote-skip, from 0 to 100. | `setskippercent`, `skippercent`, `setskipratio` |
| `prefix` | `/prefix` | Sets a prefix for this server, or `NONE` to use the default. | `setprefix` |
| `queuetype` | `/queuetype` | Sets how the queue orders songs: `linear` (in the order added) or `fair` (takes turns between the people who added them). | — |
| `nplayout` | `/nplayout` | Sets the now-playing layout for this server: `full`, `minimal`, or `inherit` from `config.txt`. | — |
| `npbuttons` | `/npbuttons` | Turns now-playing buttons `on` or `off` for this server, or `inherit` from `config.txt`. | — |

## Owner

| Command | Slash | What it does | Aliases |
|---|---|---|---|
| `clearchannel` | `/clearchannel` | Deletes messages in the music text channel set with `settc`. Limits come from `commands.clearChannel`. | — |
| `playlist` | — | Manages playlists: `make`, `append`, `delete`, `setdefault`, and `all` to list them. | — |
| `autoplaylist` | — | Sets the playlist the bot plays when the queue is empty, or `NONE`. | — |
| `setname` | — | Changes the bot's name. | — |
| `setavatar` | — | Changes the bot's avatar from an image link. | — |
| `setgame` | — | Sets the bot's status text. Use `setgame stream`, `listen` or `watch` for other activity types. | — |
| `setstatus` | — | Sets online, idle, do not disturb or invisible. | — |
| `debug` | — | Shows debug information to paste into a bug report. | — |
| `shutdown` | — | Shuts the bot down safely. | — |
| `eval` | — | Runs code as the bot. Off unless `dangerous.eval = true`, and it needs a script engine that Java 25 doesn't include; see the warning below. | — |

!!! danger "Leave `eval` off"
    `eval` runs any code you give it with full access to the bot and the computer it runs on. It's off by default
    (`dangerous.eval = false`). If someone asks you to turn it on, they're very likely trying to take over your bot.
