---
description: Use the bot with Discord's slash commands, with suggestions as you type.
---

# Slash commands

Type `/` in any channel the bot can see, and Discord lists the bot's commands with a short description of each.
Pick one, fill in the options Discord shows, and press Enter. You don't need to remember a prefix or the exact
syntax.

<figure class="oa-media oa-media--pending" markdown>
GIF: typing /play, picking a suggestion, and the song starting
</figure>

## How to use them

- **`/play`** takes a song name, a link, or a file path. As you type a name, the bot suggests up to 10 matching
  search results, so you can pick the right one before it plays.
- **`/queue`**, **`/history`** and **`/playlists`** answer with messages that have buttons for paging and for
  playing songs, instead of a wall of text.
- **`/settings`** opens a settings panel for admins, with buttons for each server setting.
- Error messages and panels only you need, such as "You must be in the same voice channel", are shown only to
  you, so the channel stays tidy.

The full list, with the matching prefix commands, is on the [Commands](../reference/commands.md) page.

## Prefix commands still work

Almost every slash command also works as a text command, and most owner tools are text-only:
`@JMusicBot play never gonna give you up`. Use whichever you like. Text commands need
[Message Content Intent](../setup/bot-token.md#turn-on-message-content-intent).

## When commands appear

The bot registers its slash commands with Discord when it starts, and only sends them again when they change
after an update. New or changed commands can take a minute to show up. If they don't appear at all, see
[Troubleshooting](../help/troubleshooting.md#slash-commands-dont-show-up).

## Who can use them

Discord shows every command to everyone, but the bot checks permissions when you run one. Music commands are for
everyone, DJ commands for the [DJ role](dj-role.md), admin commands for people with Manage Server, and owner
commands for the bot owner. If you're not allowed, the bot tells you, privately.

## Settings

Slash commands don't have settings of their own. The prefix for text commands is `commands.prefix` in
[config.txt](../reference/config.md#commands), and each server can set its own with `/prefix`.

Next: [Now-playing panel](now-playing.md).
