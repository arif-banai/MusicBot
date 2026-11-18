<img align="right" src="https://i.imgur.com/zrE80HY.png" height="200" width="200">

# JMusicBot
> [!NOTE]
> This is a fork of [JMusicBot](https://github.com/jagrosh/MusicBot) from jagrosh
> I started this because it was not maintained and was not working anymore.

[![Downloads](https://img.shields.io/github/downloads/arif-banai/MusicBot/total.svg)](https://github.com/arif-banai/MusicBot/releases/latest)
[![Stars](https://img.shields.io/github/stars/arif-banai/MusicBot.svg)](https://github.com/arif-banai/MusicBot/stargazers)
[![Release](https://img.shields.io/github/release/arif-banai/MusicBot.svg)](https://github.com/arif-banai/MusicBot/releases/latest)
[![License](https://img.shields.io/github/license/arif-banai/MusicBot.svg)](https://github.com/arif-banai/MusicBot/blob/master/LICENSE)
[![Discord](https://discordapp.com/api/guilds/1453856673004392634/widget.png?v=1)](https://discord.gg/cyyUxNmmx6) <br>
[![CircleCI](https://dl.circleci.com/status-badge/img/gh/arif-banai/MusicBot/tree/master.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/arif-banai/MusicBot/tree/master)
[![Build and Test](https://github.com/arif-banai/MusicBot/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/arif-banai/MusicBot/actions/workflows/build-and-test.yml)
[![CodeFactor](https://www.codefactor.io/repository/github/arif-banai/musicbot/badge)](https://www.codefactor.io/repository/github/arif-banai/musicbot)

A cross-platform Discord music bot with a clean interface, and that is easy to set up and run yourself!

## ⚠️ Check your Java Version and other requirements

This version of JMusicBot changes/updates various dependencies. To ensure your bot continues to function correctly, please note the following mandatory changes:

*   **Java 25 Minimum:** The bot now requires **Java 25 or higher**. 

*   **LibDave/udpqueue:** You **must** have **glibc >= 2.38**. *If you are using Docker, this is already handled for you.*
*   **Privileged Gateway Intents:** You **must** enable the **Message Content Intent** in your [Discord Developer Portal](https://discord.com/developers/applications).
    *   *Navigate to: Your Application > Bot > Privileged Gateway Intents > Toggle "Message Content Intent" to ON.*
    *   *Without this, the bot will not see your commands.*


[![Setup](http://i.imgur.com/VvXYp5j.png)](https://arif-banai.github.io/MusicBot/setup/)

## Features
  * Easy to run (just make sure Java is installed, and run!)
  * Fast loading of songs
  * No external keys needed (besides a Discord Bot token)
  * Smooth playback
  * Server-specific setup for the "DJ" role that can moderate the music
  * Clean and beautiful menus
  * Supports many sites, including Youtube, Soundcloud, and more
  * Supports many online radio/streams
  * Supports local files
  * Playlist support (both web/youtube, and local)

## Supported sources and formats
JMusicBot supports all sources and formats supported by [lavaplayer](https://github.com/lavalink-devs/lavaplayer#supported-formats):
### Sources
  * YouTube
  * SoundCloud
  * Bandcamp
  * Vimeo
  * Twitch streams
  * Local files
  * HTTP URLs
### Formats
  * MP3
  * FLAC
  * WAV
  * Matroska/WebM (AAC, Opus or Vorbis codecs)
  * MP4/M4A (AAC codec)
  * OGG streams (Opus, Vorbis and FLAC codecs)
  * AAC streams
  * Stream playlists (M3U and PLS)

## Example
![Loading Example...](https://i.imgur.com/kVtTKvS.gif)

## Quick start
1. Download `JMusicBot-<version>.jar` from the [latest release](https://github.com/arif-banai/MusicBot/releases/latest) and put it in a folder of its own.
2. Run it from that folder: `java -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED -jar JMusicBot-0.8.0.jar` (in Windows PowerShell, quote each option; the setup guide shows how)
3. On the first run the bot asks for your bot token and your Discord user ID, then saves them to `config.txt`.

The [setup guide](https://arif-banai.github.io/MusicBot/setup/) walks through every step, from making the bot token to inviting the bot. To run it in a container, see [Run with Docker](https://arif-banai.github.io/MusicBot/setup/docker/).

## Documentation
The full documentation lives at **[arif-banai.github.io/MusicBot](https://arif-banai.github.io/MusicBot/)**:

* [Setup guide](https://arif-banai.github.io/MusicBot/setup/): Java, the bot token, inviting the bot and the first run
* [Run with Docker](https://arif-banai.github.io/MusicBot/setup/docker/): the image, volumes and Docker Compose
* [Commands](https://arif-banai.github.io/MusicBot/reference/commands/): every prefix and slash command
* [config.txt](https://arif-banai.github.io/MusicBot/reference/config/): every setting, with its default
* [yt-dlp library](https://arif-banai.github.io/MusicBot/features/ytdlp-library/): download songs that won't stream and play them from disk
* [Performance tuning](https://arif-banai.github.io/MusicBot/reference/performance/): JVM flags and audio buffers
* [Proxy](https://arif-banai.github.io/MusicBot/reference/proxy/): send the bot's traffic through a proxy
* [Troubleshooting](https://arif-banai.github.io/MusicBot/help/troubleshooting/): fixes for common problems
* [Coming from JMusicBot](https://arif-banai.github.io/MusicBot/migrating/): what changed if you ran the original bot

## Development Workflow

This project follows a **trunk-based development** workflow. The `master` branch is always releasable, and all work happens in short-lived branches:

- **`feature/<slug>`** - New features (e.g., `feature/new-player-ui`)
- **`fix/<slug>`** - Bug fixes (e.g., `fix/youtube-oauth`)
- **`chore/<slug>`** - Maintenance tasks (e.g., `chore/update-deps`)
- **`deps/<slug>`** - Dependency experiments (e.g., `deps/youtube-source-pr195`)
- **`release/<version>`** - Release stabilization (optional, e.g., `release/0.6.3`)

Branch names are automatically validated by CI to ensure consistency. For detailed information about the development workflow, branch naming rules, and best practices, see [DEVELOPMENT_WORKFLOW.md](docs/DEVELOPMENT_WORKFLOW.md).

## Questions/Suggestions/Bug Reports
**Please read the [Issues List](https://github.com/arif-banai/MusicBot/issues) before suggesting a feature**. 

If you have a question, need troubleshooting help, or want to brainstorm a new feature, please start a [Discussion](https://github.com/arif-banai/MusicBot/discussions).

The Discord server is also available for questions and suggestions. [Click here to join](https://discord.gg/cyyUxNmmx6).

 If you'd like to suggest a feature or report a reproducible bug, please open an [Issue](https://github.com/arif-banai/MusicBot/issues) on this repository. If you like this bot, be sure to add a star to the libraries that make this possible: 
 - [**JDA**](https://github.com/DV8FromTheWorld/JDA)
 - [**lavaplayer**](https://github.com/lavalink-devs/lavaplayer)
 - [**youtube-source**](https://github.com/lavalink-devs/youtube-source)

## Editing
This bot (and the source code here) might not be easy to edit for inexperienced programmers. The main purpose of having the source public is to show the capabilities of the libraries, to allow others to understand how the bot works, and to allow those knowledgeable about java, JDA, and Discord bot development to contribute. There are many requirements and dependencies required to edit and compile it, and there will not be support provided for people looking to make changes on their own. Instead, consider making a feature request (see the above section). If you choose to make edits, please do so in accordance with the Apache 2.0 License.
