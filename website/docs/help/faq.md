---
description: Short answers to common questions about JMusicBot.
---

# FAQ

<!-- owner adds questions at checkpoint 2 -->

## Is this the same as the original JMusicBot?

It's a fork of [jagrosh's JMusicBot](https://github.com/jagrosh/MusicBot), started because the original stopped
being updated and stopped working. It keeps the same idea and most commands, and adds slash commands, a
now-playing panel with buttons, history, Docker images and more. See
[Coming from JMusicBot](../migrating.md).

## Does it cost anything?

No. The bot is free and open source under the Apache 2.0 license. The only cost is wherever you run it; see
[Hosting](../setup/hosting.md).

## Can one bot play in several servers at once?

Yes. One copy can be in many servers, and each server has its own queue, volume and settings. Each server can only
have one voice channel playing at a time.

## Can I play Spotify links?

Not today. Spotify doesn't allow streaming its music to other apps. Use a YouTube or SoundCloud link, or a playlist
file with song names.

## Can it play my own music files?

Yes. Attach an audio file to a `play` message, or give `play` the path of a file on the bot's computer. You can
also put file paths in a [playlist](../features/queue-history-playlists.md#playlists). The bot owner can turn this
off with `playback.audioSources.local = false`.

## How do I turn off the "new version available" messages?

Set `updates.alerts = false` in `config.txt` and restart the bot.

## Can I run it without the desktop window?

Yes. Add `-Dnogui=true` to the `java` command, or set `gui.enabled = false`. On a server with no screen, and in
Docker, it already runs without one. See [Desktop app](../features/gui.md).

## Can I run it on a NAS, Unraid or a Raspberry Pi?

Yes, with [Docker](../setup/docker.md). The image works on amd64 and ARM64. Make sure the folder you mount is
writable by the container (user 10001), or you'll see "Another instance of JMusicBot is already running".

## Do I need to keep my computer on?

The bot plays music only while it's running. To have it available all the time, run it on something that stays on;
see [Hosting](../setup/hosting.md) and [Run as a service](../setup/service.md).

## How do I report a bug or ask for a feature?

Search the [issues](https://github.com/arif-banai/MusicBot/issues) first. Open a new issue for a bug you can
reproduce, or start a [discussion](https://github.com/arif-banai/MusicBot/discussions) for questions and ideas.
Check [Things that won't be added](../reference/wont-add.md) before asking for a feature.
