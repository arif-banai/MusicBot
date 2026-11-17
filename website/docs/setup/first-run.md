---
description: Download the bot, start it, and answer its two questions.
---

# First run

## Download the bot

Go to the [latest release](https://github.com/arif-banai/MusicBot/releases/latest) and download
`JMusicBot-0.8.0-All.jar` (the version number changes with each release). Put it in a folder of its own, such as
`Documents/JMusicBot` or `/home/you/jmusicbot`. The bot keeps its settings, playlists and logs in that folder.

## Start it

Open a terminal in that folder and run the command for your system.

=== "Windows (PowerShell)"

    PowerShell needs each `-D` option in quotes:

    ```powershell
    java "-Dfile.encoding=UTF-8" "--enable-native-access=ALL-UNNAMED" -jar .\JMusicBot-0.8.0-All.jar
    ```

=== "Windows (Command Prompt)"

    ```bat
    java -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED -jar JMusicBot-0.8.0-All.jar
    ```

=== "macOS and Linux"

    ```bash
    java -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED -jar JMusicBot-0.8.0-All.jar
    ```

`-Dfile.encoding=UTF-8` makes song titles in other alphabets show correctly. `--enable-native-access` lets the
audio libraries load without warnings.

On a computer with a screen, the bot opens the [desktop app](../features/gui.md) window. On a server with no
screen, or to keep it in the terminal, add `-Dnogui=true` before `-jar`.

## Answer its two questions

The first time, there's no `config.txt` yet, so the bot asks for what it needs:

```text
Please provide a bot token.
Bot Token:
```

Paste the token from [Get a bot token](bot-token.md) and press Enter. Then:

```text
Owner ID was missing, or the provided owner ID is not valid.
Please provide the User ID of the bot's owner.
Owner User ID:
```

Paste your ID from [Find your user ID](user-id.md) and press Enter. In the desktop app, these questions appear
as pop-up boxes instead.

The bot then writes `config.txt` in the folder, with your answers and every other setting at its default, and
connects to Discord.

If you leave an answer empty or paste something that isn't valid, the bot stops, for example with
`Invalid User ID! Exiting.` Start it again; it asks for whatever is still missing. If the bot was started without
a terminal to ask in (for example in the background), it stops with `No token provided! Exiting.`

## What you'll see

When it's connected, the bot logs that it's ready. If it isn't in any server yet, it also prints an invite link:

```text
This bot is not on any guilds! Use the following link to add the bot to your guilds!
```

Use that link in the next step. The folder now also has `serversettings.json`, where the bot keeps per-server
settings such as the DJ role. Leave it alone; use commands to change those settings.

If the bot stops with "Your bot is missing required Discord intents!", turn on Message Content Intent (see
[Get a bot token](bot-token.md#turn-on-message-content-intent)) and start it again. For other errors, see
[Troubleshooting](../help/troubleshooting.md).

To change settings later, stop the bot, edit `config.txt` (see the [config.txt reference](../reference/config.md)),
and start it again.

Next: [Invite the bot](invite.md).
