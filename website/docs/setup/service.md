---
description: Keep the bot running in the background and start it automatically.
---

# Run as a service

Once the bot works, you'll want it to keep running after you close the terminal, and to start again after a reboot
or a crash. Do the [first run](first-run.md) by hand first, so `config.txt` exists; a service can't answer the
bot's first-run questions.

Using Docker? Compose's `restart: unless-stopped` already does this; see [Run with Docker](docker.md).

## Linux with systemd

These steps assume the bot lives in `/home/jmusicbot/bot` and runs as a user called `jmusicbot`. Change the user
and paths to match yours.

1. Create the service file:

    ```bash
    sudo nano /etc/systemd/system/jmusicbot.service
    ```

2. Paste this in and save:

    ```ini
    [Unit]
    Description=JMusicBot
    Wants=network-online.target
    After=network-online.target

    [Service]
    User=jmusicbot
    Group=jmusicbot
    WorkingDirectory=/home/jmusicbot/bot
    ExecStart=/usr/bin/env java -Dfile.encoding=UTF-8 -Dnogui=true -XX:+UseZGC -XX:+AlwaysPreTouch --enable-native-access=ALL-UNNAMED -jar JMusicBot-0.8.0-All.jar
    Restart=always
    RestartSec=10

    [Install]
    WantedBy=multi-user.target
    ```

3. Start it, and make it start on boot:

    ```bash
    sudo systemctl daemon-reload
    sudo systemctl enable --now jmusicbot
    ```

Useful commands:

| Command | What it does |
|---|---|
| `sudo systemctl status jmusicbot` | Shows whether it's running. |
| `sudo journalctl -u jmusicbot -f` | Follows the bot's log. |
| `sudo systemctl restart jmusicbot` | Restarts it, for example after editing `config.txt`. |
| `sudo systemctl stop jmusicbot` | Stops it. |

When you update, change the jar name in `ExecStart`, then run `sudo systemctl daemon-reload` and restart.

## Windows with Task Scheduler

1. In the bot's folder, create `start.bat`:

    ```bat
    @echo off
    cd /d "%~dp0"
    java -Dfile.encoding=UTF-8 -Dnogui=true -XX:+UseZGC -XX:+AlwaysPreTouch --enable-native-access=ALL-UNNAMED -jar JMusicBot-0.8.0-All.jar
    ```

2. Open **Task Scheduler** and choose **Create Task**.
3. On **General**, name it `JMusicBot` and choose **Run whether user is logged on or not**.
4. On **Triggers**, add **At startup**.
5. On **Actions**, add **Start a program** and pick `start.bat`. Set **Start in** to the bot's folder.
6. On **Settings**, turn on **If the task fails, restart every** 1 minute, and turn off **Stop the task if it runs
   longer than**.

To stop the bot, end the task in Task Scheduler, or use the owner-only `shutdown` command in Discord.

## macOS

The simplest option is to keep a Terminal window open, or run the bot in Docker. For a background service, create
a `launchd` agent that runs the same `java` command with the bot's folder as its working directory.

Next: [Hosting](hosting.md) covers where to run the bot 24/7.

Credit: adapted from the original JMusicBot guide by [jagrosh](https://github.com/jagrosh/MusicBot).
