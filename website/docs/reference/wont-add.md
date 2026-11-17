---
description: Requests that don't fit JMusicBot, and why.
---

# Things that won't be added

JMusicBot aims to do one job well: play music in your Discord server from a bot you run yourself, with nothing to
set up beyond a bot token. Some requests come up often but don't fit that aim. Please check this list before you
open a feature request.

<!-- owner review at checkpoint 2: confirm this list. Spotify is left off on purpose while the
     feature/spotify-support work is open. -->

## Features that aren't about music

Moderation, welcome messages, games, image generation, levels and similar features won't be added. Plenty of bots
already do those well, and you can run one alongside JMusicBot. Keeping this bot focused on music keeps it small,
fast and easy to set up.

## Features that need more API keys

Anything that would make you sign up for another service and paste in another key, beyond the Discord bot token,
is out of scope for the bot's core. Extra keys make setup harder for everyone, including people who don't want the
feature. Optional features that work without a key, or that use a tool you install yourself (like the
[yt-dlp library](../features/ytdlp-library.md)), are a different matter and are welcome as suggestions.

## A public bot you can invite

There's no shared, hosted copy of JMusicBot to invite. The point of the project is that you run your own, so it
only answers to you and keeps working when other music bots get shut down.

## Suggesting something else

If your idea isn't on this list, search the [issues](https://github.com/arif-banai/MusicBot/issues) first, then
start a [discussion](https://github.com/arif-banai/MusicBot/discussions) or ask in the
[Discord server](https://discord.gg/cyyUxNmmx6).

Credit: adapted from the original JMusicBot's list by [jagrosh](https://github.com/jagrosh/MusicBot).
