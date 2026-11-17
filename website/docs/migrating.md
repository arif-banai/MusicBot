---
description: Move from the original JMusicBot to this version, and what's new.
---

# Coming from JMusicBot

This version continues the original [JMusicBot by jagrosh](https://github.com/jagrosh/MusicBot), which stopped
being updated. The commands you know still work, your playlists carry over, and your `config.txt` is updated for
you. You do need a newer Java and one Discord setting.

## What you need to change

1. **Java 25.** The original ran on Java 11; this version needs Java 25 or newer. See
   [Install Java 25](setup/java.md). With Docker you don't need Java at all.
2. **Linux: glibc 2.38.** The new voice libraries need glibc 2.38 or newer: Ubuntu 24.04, Debian 13 or later.
   Older systems can use [Docker](setup/docker.md).
3. **Message Content Intent.** Turn it on for your bot in the Developer Portal, under **Bot** →
   **Privileged Gateway Intents**. Without it the bot can't connect. See
   [Get a bot token](setup/bot-token.md#turn-on-message-content-intent).
4. **The new jar.** Download the [latest release](https://github.com/arif-banai/MusicBot/releases/latest) and put
   it in your bot's folder, next to your old `config.txt`, `Playlists` folder and `serversettings.json`. Start it
   with the command on [First run](setup/first-run.md).

## Your config.txt

The original used a flat `config.txt` (`token = ...`, `maxtime = ...`). This version uses nested blocks
(`discord { token = ... }`). You don't need to rewrite it: on its first start, the bot updates the file
automatically. It:

- copies your original to `config.txt.bak`,
- rewrites `config.txt` in the new layout, keeping your values (your token, owner, prefix, limits and aliases),
- adds the new settings with their defaults,
- logs `Config migration from version 0 → 2 completed successfully`.

Some settings have new names, for example `maxtime` is now `playback.maxTrackSeconds`. The
[config.txt reference](reference/config.md) lists them all. The old `lyrics.default` setting is gone.

## Your playlists and server settings

Your `Playlists` folder works as before: one song per line, `#` for comments, and `#shuffle` to shuffle. Keep
`serversettings.json` too; it holds each server's DJ role, channels, prefix and volume.

If you set a different folder with the old `playlistsfolder` setting, it's moved over to `paths.playlistsFolder`.

## Re-invite for slash commands

Slash commands need the `applications.commands` scope, which today's `bot` scope includes. If your bot was invited
years ago and slash commands don't appear, invite it again with a fresh link from
[Invite the bot](setup/invite.md); you don't need to remove it first. Text commands like
`@JMusicBot play` keep working either way.

## New things to try

- [Slash commands](features/slash-commands.md): type `/play` and pick from suggestions.
- [Now-playing panel](features/now-playing.md): buttons under every song, two layouts, and an optional progress
  bar.
- [Queue, history and playlists](features/queue-history-playlists.md): `/history` to replay recent songs, fair
  queues, a favorites playlist, and playlist browsing with buttons.
- [yt-dlp library](features/ytdlp-library.md): keep songs that won't stream by downloading them with your own
  yt-dlp.
- [Docker](setup/docker.md): ready-made images for amd64 and ARM64.
- [Desktop app](features/gui.md): a window with the log, performance and settings.
- New commands: `history`, `library`, `nplayout`, `npbuttons` and `clearchannel`; `lyrics` is gone. See [Commands](reference/commands.md).

## What's gone or different

- **Lyrics.** The `lyrics` command was removed; the lyrics service it used no longer exists.
- **Default prefix.** Still a mention of the bot (`@JMusicBot play`); if you set your own prefix, it's kept.

Questions? See the [FAQ](help/faq.md) or [Troubleshooting](help/troubleshooting.md).
