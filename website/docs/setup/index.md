---
description: Everything you need to run your own JMusicBot, in five steps.
---

# Set up your bot

JMusicBot runs on your own computer or server and joins your Discord server as a bot. Setting it up takes about
ten minutes. You need:

- A computer that can stay on while you want music: your PC, a home server, a Raspberry Pi-class board or a small
  VPS. See [Hosting](hosting.md).
- Java 25, or Docker.
- A Discord account that can add bots to your server (you need **Manage Server** there).

You don't need any API keys besides the Discord bot token.

## The steps

1. [Install Java 25](java.md). Check you have the right version, and on Linux check glibc.
2. [Get a bot token](bot-token.md). Create the bot in the Discord Developer Portal and turn on Message Content
   Intent.
3. [Find your user ID](user-id.md). The bot uses it to know you're the owner.
4. [Run the bot for the first time](first-run.md). Download the jar, start it, and paste in the token and your ID
   when it asks.
5. [Invite the bot](invite.md) to your server, join a voice channel and type `/play` with a song name.

Once it works, you can keep it running in the background with [Run as a service](service.md).

## Rather use Docker?

If you already use Docker, skip installing Java: the image has everything the bot needs, on both x86 and ARM64
machines. Do steps 2 and 3 above, then follow [Run with Docker](docker.md), then step 5.

## Coming from the original JMusicBot?

Your playlists carry over, and your `config.txt` is updated automatically, but you need a newer Java and one new
Discord setting. Read [Coming from JMusicBot](../migrating.md) first.

Next: [Install Java 25](java.md).
