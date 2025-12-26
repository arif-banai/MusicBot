<img align="right" src="https://i.imgur.com/zrE80HY.png" height="200" width="200">

# JMusicBot
> [!NOTE]
> This is a fork of [JMusicBot](https://github.com/jagrosh/MusicBot) from jagrosh
> I started this because it was not maintained and was not working anymore.

[![Downloads](https://img.shields.io/github/downloads/arif-banai/MusicBot/total.svg)](https://github.com/arif-banai/MusicBot/releases/latest)
[![Stars](https://img.shields.io/github/stars/arif-banai/MusicBot.svg)](https://github.com/arif-banai/MusicBot/stargazers)
[![Release](https://img.shields.io/github/release/arif-banai/MusicBot.svg)](https://github.com/arif-banai/MusicBot/releases/latest)
[![License](https://img.shields.io/github/license/arif-banai/MusicBot.svg)](https://github.com/arif-banai/MusicBot/blob/master/LICENSE)
[![Discord](https://discordapp.com/api/guilds/1453856673004392634/widget.png)](https://discord.gg/cyyUxNmmx6) <br>
[![CircleCI](https://dl.circleci.com/status-badge/img/gh/arif-banai/MusicBot/tree/master.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/arif-banai/MusicBot/tree/master)
[![Build and Test](https://github.com/arif-banai/MusicBot/actions/workflows/build-and-test.yml/badge.svg)](https://github.com/arif-banai/MusicBot/actions/workflows/build-and-test.yml)
[![CodeFactor](https://www.codefactor.io/repository/github/arif-banai/musicbot/badge)](https://www.codefactor.io/repository/github/arif-banai/musicbot)

A cross-platform Discord music bot with a clean interface, and that is easy to set up and run yourself!

## ⚠️ Important Migration Notice (JDA 6 Update)

This version of JMusicBot includes a major infrastructure update to **JDA 6.2.0** and **Lavaplayer 2.2.6**. To ensure your bot continues to function correctly, please note the following mandatory changes:

*   **Java 17 Minimum:** The bot now requires **Java 17 or higher**. Please update your hosting environment (check `java -version`) before running the new JAR.
*   **Privileged Gateway Intents:** You **must** enable the **Message Content Intent** in your [Discord Developer Portal](https://discord.com/developers/applications).
    *   *Navigate to: Your Application > Bot > Privileged Gateway Intents > Toggle "Message Content Intent" to ON.*
    *   *Without this, the bot will not see your commands.*
*   **Audio Provider Update:** We have switched to the `dev.arbjerg` Lavaplayer fork and added the `dev.lavalink.youtube` source manager. This provides the most stable and up-to-date support for YouTube and other modern audio sources.
*   **Dependency Cleanup:** Support for niche/legacy sources provided by the old DuncteBot library (such as TikTok or Reddit audio) has been removed to reduce the project's footprint and improve stability. Standard sources (YouTube, SoundCloud, Bandcamp, Twitch, etc.) are unaffected.

[![Setup](http://i.imgur.com/VvXYp5j.png)](https://jmusicbot.com/setup)

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
JMusicBot supports all sources and formats supported by [lavaplayer](https://github.com/sedmelluq/lavaplayer#supported-formats):
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

## Setup
Please see the [Setup Page](https://jmusicbot.com/setup) to run this bot yourself!

## Questions/Suggestions/Bug Reports
**Please read the [Issues List](https://github.com/arif-banai/MusicBot/issues) before suggesting a feature**. If you have a question, need troubleshooting help, or want to brainstorm a new feature, please start a [Discussion](https://github.com/arif-banai/MusicBot/discussions). If you'd like to suggest a feature or report a reproducible bug, please open an [Issue](https://github.com/arif-banai/MusicBot/issues) on this repository. If you like this bot, be sure to add a star to the libraries that make this possible: [**JDA**](https://github.com/DV8FromTheWorld/JDA) and [**lavaplayer**](https://github.com/lavalink-devs/lavaplayer)!

## Editing
This bot (and the source code here) might not be easy to edit for inexperienced programmers. The main purpose of having the source public is to show the capabilities of the libraries, to allow others to understand how the bot works, and to allow those knowledgeable about java, JDA, and Discord bot development to contribute. There are many requirements and dependencies required to edit and compile it, and there will not be support provided for people looking to make changes on their own. Instead, consider making a feature request (see the above section). If you choose to make edits, please do so in accordance with the Apache 2.0 License.
