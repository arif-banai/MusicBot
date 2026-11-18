---
template: home.html
title: Home
hide:
  - navigation
  - toc
---

<div class="oa-feature" markdown>
<div markdown>
## A control panel for every song

Pause, skip, change the volume or save a favorite with buttons right under the song, without typing a command.
Choose the full layout or a compact one for each server.

[See the now-playing panel](features/now-playing.md)
</div>

<figure class="oa-media" markdown>
![The now-playing panel in Discord: pressing Pause and Resume, Skip to the next song, then Vol + raising the volume from 40% to 50%](assets/media/np-buttons.webp){ width="560" }
</figure>
</div>

<div class="oa-feature oa-feature--flip" markdown>
<div markdown>
## Slash commands with suggestions

Type `/play` and pick the right song from the suggestions before it plays. Every command shows up when you type
`/`, and text commands like `@JMusicBot play` still work.

[See slash commands](features/slash-commands.md)
</div>

<figure class="oa-media" markdown>
![Typing /play in Discord, picking a song from the suggestions as you type, and the bot starting to play it](assets/media/slash-play.webp){ width="760" }
</figure>
</div>

<div class="oa-feature" markdown>
<div markdown>
## Replay what just played

`/history` lists recent songs with buttons to play one again or save them all as a playlist. Fair queues take
turns between the people adding songs.

[See queue, history and playlists](features/queue-history-playlists.md)
</div>

<figure class="oa-media" markdown>
![The /history panel in Discord: picking a recently played song and pressing Play Now, then Save as playlist naming it and the bot confirming](assets/media/history.webp){ width="560" }
</figure>
</div>

<div class="oa-feature oa-feature--flip" markdown>
<div markdown>
## Keep songs that won't stream

Connect your own yt-dlp and the bot can download a song when it won't play, then play it from disk next time.
Browse what you've saved with `/library panel`.

[See the yt-dlp library](features/ytdlp-library.md)
</div>

<figure class="oa-media" markdown>
![Pressing Download on the now-playing panel, the bot confirming the song is saved to the library, then searching /library panel and finding it](assets/media/ytdlp-download.webp){ width="560" }
</figure>
</div>

<div class="oa-feature" markdown>
<div markdown>
## You decide who's in charge

Pick a DJ role to control playback for everyone, while anyone can still add songs and vote to skip. Keep music
commands to one channel if you like.

[See the DJ role](features/dj-role.md)
</div>

<figure class="oa-media" markdown>
![Typing /setdj in Discord, picking the DJ role, and the bot confirming that DJ commands can now be used by that role](assets/media/setdj.webp){ width="830" }
</figure>
</div>

<div class="oa-feature oa-feature--flip oa-feature--solo" markdown>
<div markdown>
## See what the bot is doing

On a computer with a screen, the bot opens a window with its log, audio performance and settings. On a server it
runs quietly in the terminal or in Docker.

[See the desktop app](features/gui.md)
</div>
</div>
