---
description: The window the bot opens on a computer with a screen.
---

# Desktop app

When you run the bot on a computer with a screen, it opens a window. The window shows what the bot is doing and
lets you change its settings without opening `config.txt`. Closing the window stops the bot.

<figure class="oa-media oa-media--pending" markdown>
Screenshot: the desktop app window on its Performance tab
</figure>

## Tabs

| Tab | What it shows |
|---|---|
| Console | The bot's log, the same text you'd see in a terminal. |
| Status | Whether the bot is connected, and the servers it's in. |
| Performance | Audio timing, to spot stutters as they happen. |
| System | CPU, memory and Java's memory clean-ups (garbage collection). |
| Sources | How songs from each source are loading: successes, failures and load times. |
| Settings | The window's own look: theme and text size. |
| Config | The bot's settings from `config.txt`, editable in the window. |

The Performance and System tabs are useful when you're [tuning for smooth audio](../reference/performance.md).

## Running without the window

On a server with no screen, the bot runs in the terminal automatically. To keep it in the terminal on a computer
with a screen, either:

- add `-Dnogui=true` to the `java` command (see [First run](../setup/first-run.md)), or
- set `gui.enabled = false` in `config.txt`.

The Docker image always runs without the window.

## Who can use it

Whoever is at the computer running the bot. The window isn't part of Discord.

## Settings

| Setting | Default | What it does |
|---|---|---|
| `gui.enabled` | `true` | Open the window when there's a screen. |
| `gui.theme` | `"dark"` | `light`, `dark`, `darcula` or `intellij`. |
| `gui.fontSize` | `12` | Text size, from 8 to 24. |

Next: [Commands](../reference/commands.md).
