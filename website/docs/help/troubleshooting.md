---
description: Fixes for the most common problems, by what you see.
---

# Troubleshooting

Find what you're seeing below. If it isn't here, check the bot's console or log for the first error, then ask in
the [Discord server](https://discord.gg/cyyUxNmmx6) or start a
[discussion](https://github.com/arif-banai/MusicBot/discussions). Include your OS, `java -version`, the bot's
version and the error text.

## The bot won't start

### "Your bot is missing required Discord intents!"

Discord refused the connection because Message Content Intent is off.

**Fix:** in the [Developer Portal](https://discord.com/developers/applications), open your application, go to
**Bot**, turn on **Message Content Intent** and save. Then start the bot again. See
[Get a bot token](../setup/bot-token.md#turn-on-message-content-intent).

### `UnsupportedClassVersionError` or "has been compiled by a more recent version"

Your Java is too old.

**Fix:** install [Java 25](../setup/java.md) and check that `java -version` shows 25 in the same terminal you start
the bot from. If you have several Java versions, use the full path to Java 25's `java`.

### Errors about glibc, `GLIBC_2.38`, libdave or udpqueue on Linux

The voice libraries need glibc 2.38 or newer, and your system has an older one. Check with `ldd --version`.

**Fix:** upgrade to a release with glibc 2.38 or newer (Ubuntu 24.04, Debian 13), or use [Docker](../setup/docker.md),
which brings its own. On Debian and Ubuntu also install `libopus0` and `libsodium23`.

### "Another instance of JMusicBot is already running."

The bot keeps a lock file, `.jmusicbot.lock`, in its folder so two copies can't answer the same commands.

**Fix:** close the other copy, including any service or container running the bot. If none is running, the bot
probably can't write to its folder: in Docker on a NAS or Unraid, make sure the mounted folder is writable by user
10001, or run the container as your own user (see [Run with Docker](../setup/docker.md#pick-a-folder)).

### "No token provided! Exiting."

The bot asked for a token and got none. This happens when it's started in the background before `config.txt`
exists.

**Fix:** start it once in a terminal (in Docker, with `-it`) and answer its questions, or create `config.txt`
yourself. See [First run](../setup/first-run.md).

### It stops right after "Loaded config", or says the token is invalid

The token in `config.txt` is wrong, has extra spaces, or was reset.

**Fix:** reset the token in the Developer Portal, paste the new one into `config.txt` between the quotes, and start
the bot again.

## The bot is online but doesn't respond

### It ignores text commands

**Fix:** turn on Message Content Intent (above). Also check that you're using the right prefix: by default it's a
mention, `@JMusicBot play`, not an exclamation mark. A server may have its own prefix; `/settings` shows it. If `/settc` is
set, the bot only answers in that channel.

### Slash commands don't show up

**Fix:** wait a minute after the bot starts; Discord can be slow to show new commands. If they still don't appear,
the bot was invited without the `applications.commands` scope. Invite it again with a link that has both `bot` and
`applications.commands` (see [Invite the bot](../setup/invite.md)); you don't need to remove it first. Restarting
your Discord app also helps.

### "You must be in the same voice channel to use this!"

Buttons and most music commands only work for people in the bot's voice channel. Join it first.

## Playback problems

### Songs won't play

Try these in order:

1. **Update the bot** to the [latest release](https://github.com/arif-banai/MusicBot/releases/latest). Sites
   change, and new releases keep up with them. This fixes most cases.
2. **If YouTube asks you to sign in** ("Sign in to confirm you're not a bot") or says "Video unavailable", set
   `playback.youtube.useOAuth = true` in `config.txt` and follow the sign-in steps in the
   [config.txt reference](../reference/config.md#signing-in-to-youtube).
3. **If YouTube songs still fail**, set up the [yt-dlp library](../features/ytdlp-library.md) so the bot can
   download them and play them from disk.
4. **If only one site fails**, such as SoundCloud, check the
   [issues](https://github.com/arif-banai/MusicBot/issues) for a known problem with that source.

### The bot joins, then leaves right away

The bot leaves when it's alone in the channel for `voice.aloneTimeUntilStopSeconds` (10 by default), and when the
queue ends unless `voice.stayInChannel = true`. If it leaves before any song plays, the song failed to load; see
"Songs won't play" above.

### Song titles show as `???` or garbled characters

Java isn't using UTF-8.

**Fix:** add `-Dfile.encoding=UTF-8` to the `java` command (in PowerShell, `"-Dfile.encoding=UTF-8"` in quotes).
On Linux, also run with a UTF-8 locale such as `LANG=C.UTF-8`. The Docker image already does both.

### The audio stutters or cuts out

**Fix:** start Java with ZGC (`-XX:+UseZGC -XX:+AlwaysPreTouch`), give the bot enough memory, and raise the audio
buffers if it continues. See [Performance tuning](../reference/performance.md).

## Still stuck?

Run the owner-only `debug` command; it sends a file with the bot's version, settings and system details that
helps others see what's wrong. Remove your token if you share any part of `config.txt`.
