---
description: Run JMusicBot from the Docker image, with docker run or Docker Compose.
---

# Run with Docker

The Docker image includes Java and the right system libraries, so you don't need to install Java or check glibc.
It runs on x86 (amd64) and ARM64 machines, including a Raspberry Pi 4 or 5 with a 64-bit OS.

The image is `ghcr.io/arif-banai/musicbot`. Tags:

- `latest`: the newest release.
- A version, such as `0.9.0`: that release only. Pin a version for anything you rely on, and update on purpose.
- `master`: the newest build of the `master` branch, which can be ahead of the last release. Use it to try a fix
  before it's released.

You still need a [bot token](bot-token.md) and [your user ID](user-id.md).

## Pick a folder

The container keeps `config.txt`, playlists and settings in a folder on your machine, mounted at `/musicbot`.
Create one:

```bash
mkdir -p ~/jmusicbot
```

The bot runs as user ID 10001 inside the container, so that user needs to be able to write to the folder. On Linux:

```bash
sudo chown 10001:10001 ~/jmusicbot
```

Or run the container as yourself by adding `--user "$(id -u):$(id -g)"` to `docker run` (or a `user:` line in
Compose).

## First start: answer the questions

The first time, there's no `config.txt`, so the bot asks for the token and your user ID, just like the
[first run](first-run.md) without Docker. That needs an interactive terminal, so start it once with `-it`:

```bash
docker run --rm -it \
  --name jmusicbot \
  -v ~/jmusicbot:/musicbot \
  ghcr.io/arif-banai/musicbot:latest
```

Paste the token and your ID when asked. The bot writes `~/jmusicbot/config.txt` and connects. Press Ctrl+C to stop
it once you've seen it start.

!!! note "Started it in the background first?"
    Without `-it`, the bot can't ask its questions. It stops with `No token provided! Exiting.` and doesn't create
    `config.txt`. Either run the command above once, or create `config.txt` in the folder yourself with your token
    and owner ID (see [config.txt](../reference/config.md)).

## Keep it running with Compose

Save this as `docker-compose.yml`, changing the folder path:

```yaml
services:
  jmusicbot:
    image: ghcr.io/arif-banai/musicbot:0.9.0
    container_name: jmusicbot
    volumes:
      - ~/jmusicbot:/musicbot
    restart: unless-stopped
    # environment:
    #   - JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseZGC -XX:+AlwaysPreTouch
```

Then start it in the background and follow the log:

```bash
docker compose up -d
docker compose logs -f
```

`restart: unless-stopped` brings the bot back after a crash or a reboot.

## Moving from the jar

If you already run the jar, stop it and mount its folder (the one with `config.txt` and `Playlists/`) at
`/musicbot`. The container uses your existing settings and playlists:

```bash
cd /path/to/your/jmusicbot
docker run --rm -it --user "$(id -u):$(id -g)" -v "$(pwd):/musicbot" ghcr.io/arif-banai/musicbot:latest
```

`--user` runs the container as you, so it can write to your existing files. Without it the container runs as user
10001, can't write to the folder, and stops with "Another instance of JMusicBot is already running". Alternatively,
give the folder to user 10001 with `sudo chown -R 10001:10001 /path/to/your/jmusicbot`.

## Updating

Change the version tag in `docker-compose.yml`, then:

```bash
docker compose pull
docker compose up -d
```

Your folder keeps everything, so updating doesn't touch your settings.

## Memory and Java options

The image starts Java with ZGC and AlwaysPreTouch for smooth audio. Set `JAVA_OPTS` to add a memory limit or
change the flags; see [Performance tuning](../reference/performance.md).

The image doesn't include yt-dlp. To use the [yt-dlp library](../features/ytdlp-library.md), mount a yt-dlp binary
into the container and set `ytdlp.path` to its path inside the container.

Next: [Invite the bot](invite.md).
