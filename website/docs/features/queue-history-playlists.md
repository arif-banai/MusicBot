---
description: See what's coming up, replay what just played, and save lists of songs.
---

# Queue, history and playlists

## The queue

Every song you ask for goes into the server's queue. `/queue` shows it a page at a time, with buttons to move
between pages.

<figure class="oa-media oa-media--pending" markdown>
GIF: /queue with paging buttons
</figure>

- `/remove` takes a song out by its number. `remove all` takes out everything you added.
- `/shuffle` shuffles the songs you added. DJs can also shuffle the whole queue with the Shuffle button.
- DJs can reorder with `/movetrack`, jump ahead with `/skipto`, or put a song first with `/playnext`.

### Fair turns

By default songs play in the order they were added. With `/queuetype fair`, the queue takes turns between the
people who added songs, so one person can't fill it. `/queuetype linear` switches back.

## History

The bot remembers the songs it played recently. `/history` lists them, with buttons to play one now, add it to the
queue, or add them all. DJs and the bot owner also get a button to save them as a playlist. The Prev button on the [now-playing panel](now-playing.md) uses
the same history.

<figure class="oa-media oa-media--pending" markdown>
GIF: /history, then Play Now and Save as playlist
</figure>

`playback.maxHistorySize` sets how many songs it remembers (40 by default; `0` turns history off).

## Playlists

A playlist is a text file in the bot's `Playlists` folder, one song per line. Lines can be links, search terms or
file paths. Lines starting with `#` or `//` are ignored, except `#shuffle`, which shuffles the playlist each time
it plays.

```text
#shuffle
https://www.youtube.com/watch?v=dQw4w9WgXcQ
https://soundcloud.com/some-artist/some-track
Daft Punk - Something About Us
```

- `/playlists` lists the server's playlists, with buttons to play one now, queue it, or see what's in it. DJs and
  the bot owner can also edit a playlist's songs from there.
- `play playlist <name>` plays one with a text command.

<figure class="oa-media oa-media--pending" markdown>
GIF: /playlists, browsing, then Play Now
</figure>

The bot owner manages the files with the `playlist` command (`make`, `append`, `delete`, `all`), or by editing the
files directly. `autoplaylist <name>` sets a playlist to play whenever a server's queue runs out.

### Favorites

The ⭐ button on the now-playing panel adds the current song to a playlist called `favorites`. DJs can use it.

## Who can use it

- Viewing the queue, history and playlists, and adding or removing your own songs: everyone.
- Reordering, skipping to a position, and the Shuffle button: [DJs](dj-role.md).
- Saving history as a playlist, editing playlists from `/playlists`, and the ⭐ Favorite button: DJs and the bot
  owner.
- The `playlist` command (make, append, delete) and the auto-playlist: the bot owner.

## Settings

| Setting | Default | What it does |
|---|---|---|
| `playback.maxHistorySize` | `40` | How many played songs to remember. |
| `playback.maxYouTubePlaylistPages` | `10` | How much of a YouTube playlist link to load, 100 songs per page. |
| `paths.playlistsFolder` | `"Playlists"` | Where playlist files live. |

Next: [yt-dlp library](ytdlp-library.md).
