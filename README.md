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

## ⚠️ Important Notice (Java 25)

*   **Java 25 Minimum:** The bot now requires **Java 25 or higher**. Please update your hosting environment (check `java -version`) before running the new JAR.
*   **Privileged Gateway Intents:** You **must** enable the **Message Content Intent** in your [Discord Developer Portal](https://discord.com/developers/applications).
    *   *Navigate to: Your Application > Bot > Privileged Gateway Intents > Toggle "Message Content Intent" to ON.*
    *   *Without this, the bot will not see your commands.*

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

## Docker

JMusicBot can be run using Docker for easy deployment and management. Pre-built images are available from the GitHub Container Registry. The container is configured to run headless and automatically generate a default `config.txt` on first run.

### Quick Start

#### For Existing Users (Migrating from JAR)

If you already have a directory with your `config.txt`, `Playlists/` folder, and other bot files, either provide the path to that directory or run the container from within it:

```bash
docker run --rm -it \
  --name jmusicbot \
  -v "$(pwd):/musicbot" \
  ghcr.io/arif-banai/musicbot:latest
```

This mounts your current directory as the musicbot volume, so the bot will use your existing configuration and playlists.

#### For New Users

1. **Create a directory for your bot and run the container:**
   ```bash
   mkdir -p /path/to/jmusicbot
   
   docker run --rm -it \
     --name jmusicbot \
     -v "/path/to/musicbot:/musicbot" \
     ghcr.io/arif-banai/musicbot:latest
   ```

2. **First Run:**
   - On first run, if the mounted directory is empty, the bot will automatically generate a default `config.txt` file.
   - Edit `/path/to/musicbot/config.txt` on your host and add your Discord bot token.
   - Run the container again.

#### Using Docker Compose (Optional)

If you prefer docker-compose, copy the example compose file and update the volume path:

```bash
cp docker-compose.example.yml docker-compose.yml
# Edit docker-compose.yml and update the volume path
docker-compose up -d
```

Example `docker-compose.yml`:

```yaml
services:
  jmusicbot:
    image: ghcr.io/arif-banai/musicbot:latest
    container_name: jmusicbot
    volumes:
      - /path/to/musicbot:/musicbot
    restart: unless-stopped
```

Check the [Docker Compose Example](docker-compose.example.yml) for more details.

### Important Notes

- **Config Persistence:** The `/musicbot` volume **must** be mounted for your configuration to persist. The bot reads and writes `config.txt` from `/musicbot` (the container's working directory).
- **First Run:** If `config.txt` doesn't exist, the bot will generate a default one automatically. You'll need to edit it with your bot token before the bot can start.
- **Image Tags:** 
  - Use `ghcr.io/arif-banai/musicbot:latest` for the latest build from the default branch
  - Use `ghcr.io/arif-banai/musicbot:0.6.1` (replace with actual version) to pin a specific release version
  - **Recommendation:** For production, pin your image tag rather than using `latest`
- **JAVA_OPTS:** The container uses optimized JVM flags by default (ZGC, 512MB heap). Override with `JAVA_OPTS` if needed. See [Performance Tuning](#performance-tuning) for details.


To view published images, visit: `https://github.com/arif-banai/MusicBot/pkgs/container/musicbot`

## Performance Tuning

For optimal audio quality with minimal stuttering, the following JVM and configuration options are recommended.

### JVM Flags (Recommended)

The bot works best with ZGC (Z Garbage Collector) which provides sub-millisecond pause times:

```bash
java -Xms512m -Xmx512m \
     -XX:+UseZGC -XX:+ZGenerational \
     -XX:+AlwaysPreTouch \
     -Dnogui=true \
     --enable-native-access=ALL-UNNAMED \
     -jar JMusicBot-*.jar
```

**Flag explanations:**
- `-Xms512m -Xmx512m`: Fixed heap size prevents resizing pauses
- `-XX:+UseZGC -XX:+ZGenerational`: Sub-millisecond GC pauses (requires Java 21+)
- `-XX:+AlwaysPreTouch`: Pre-allocates memory at startup to avoid page faults

The Docker image uses these flags by default. To override, set `JAVA_OPTS`:
```yaml
environment:
  - JAVA_OPTS=-Xms1g -Xmx1g -XX:+UseZGC -XX:+ZGenerational -XX:+AlwaysPreTouch
```

### Audio Buffer Configuration

The bot includes configurable audio buffers that protect against GC pauses. These can be tuned in `config.txt`:

```hocon
performance {
  # NAS buffer duration in ms (protects against JVM pauses up to this duration)
  nasBufferMs = 800
  
  # Lavaplayer frame buffer duration in ms (amount of decoded audio to buffer)
  frameBufferMs = 2000
}
```

Higher values provide more protection against stuttering but add latency. The defaults (800ms NAS, 2000ms frame buffer) should work well for most setups.

## Development Workflow

This project follows a **trunk-based development** workflow. The `master` branch is always releasable, and all work happens in short-lived branches:

- **`feature/<slug>`** - New features (e.g., `feature/new-player-ui`)
- **`fix/<slug>`** - Bug fixes (e.g., `fix/youtube-oauth`)
- **`chore/<slug>`** - Maintenance tasks (e.g., `chore/update-deps`)
- **`deps/<slug>`** - Dependency experiments (e.g., `deps/youtube-source-pr195`)
- **`release/<version>`** - Release stabilization (optional, e.g., `release/0.6.3`)

Branch names are automatically validated by CI to ensure consistency. For detailed information about the development workflow, branch naming rules, and best practices, see [DEVELOPMENT_WORKFLOW.md](docs/DEVELOPMENT_WORKFLOW.md).

## Questions/Suggestions/Bug Reports
**Please read the [Issues List](https://github.com/arif-banai/MusicBot/issues) before suggesting a feature**. If you have a question, need troubleshooting help, or want to brainstorm a new feature, please start a [Discussion](https://github.com/arif-banai/MusicBot/discussions). If you'd like to suggest a feature or report a reproducible bug, please open an [Issue](https://github.com/arif-banai/MusicBot/issues) on this repository. If you like this bot, be sure to add a star to the libraries that make this possible: [**JDA**](https://github.com/DV8FromTheWorld/JDA) and [**lavaplayer**](https://github.com/lavalink-devs/lavaplayer)!

## Editing
This bot (and the source code here) might not be easy to edit for inexperienced programmers. The main purpose of having the source public is to show the capabilities of the libraries, to allow others to understand how the bot works, and to allow those knowledgeable about java, JDA, and Discord bot development to contribute. There are many requirements and dependencies required to edit and compile it, and there will not be support provided for people looking to make changes on their own. Instead, consider making a feature request (see the above section). If you choose to make edits, please do so in accordance with the Apache 2.0 License.
