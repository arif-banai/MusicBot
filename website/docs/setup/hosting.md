---
description: Where to run JMusicBot so it's always available.
---

# Hosting

The bot only plays music while it's running, so for a bot your server can use any time, run it somewhere that
stays on. It doesn't need much: one copy serves many Discord servers.

## What it needs

| | Needs |
|---|---|
| Java | Java 25 or newer, or Docker. |
| Operating system | Windows, macOS, or Linux with glibc 2.38 or newer (Ubuntu 24.04, Debian 13 or later). Docker works on older Linux too. |
| CPU | x86-64 or ARM64. One core is enough for a few servers playing at once. |
| Memory | About 512 MB free for the bot is comfortable. ZGC and AlwaysPreTouch claim memory up front, so set `-Xmx` on a small machine. |
| Network | A steady connection. Upload speed matters more than download, since the bot streams to Discord. |
| Disk | Under 100 MB for the bot. The yt-dlp library, if you use it, grows with every song it saves. |

## Options

### Your own PC

Free and easy to start with. The bot only works while the PC is on and the bot is running, and a busy PC (for
example while gaming) can make the audio stutter. Good for trying it out or for a small group that plays music
when you're online.

### A home server or single-board computer

An old laptop, a home server, or a Raspberry Pi 4 or 5 running a 64-bit OS is a good always-on home for the bot.
It costs little to run and you control it. Use [Docker](docker.md) on a Pi; the image supports ARM64. Your home
connection's upload speed and stability decide the audio quality.

### A VPS

A small virtual server from a cloud provider keeps the bot online with a fast, steady connection. A plan with 1 CPU
and 1 GB of memory is plenty. Pick one with Ubuntu 24.04 or Debian 13 so Java 25 and glibc 2.38 are easy, or use
Docker. Watch out for "burstable" CPU plans, which can slow down at busy times; see
[Performance tuning](../reference/performance.md).

Free tiers exist, but they often have usage limits or sleep when idle, and some don't allow voice traffic. Check
the terms before you rely on one.

## After you pick

Set the bot up to start on its own: [Run as a service](service.md) for the jar, or Compose with
`restart: unless-stopped` for [Docker](docker.md).
