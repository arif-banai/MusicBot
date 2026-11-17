---
description: Install Java 25 on Windows, macOS or Linux, and check glibc on Linux.
---

# Install Java 25

JMusicBot needs **Java 25 or newer**. Older versions, including the Java 8, 11 or 17 the original JMusicBot used,
won't start it.

!!! tip "Using Docker?"
    Skip this page. The [Docker image](docker.md) already includes Java.

## Check what you have

Open a terminal (PowerShell on Windows) and run:

```bash
java -version
```

If the first line says `25` or higher, such as `java version "25.0.3"` or `openjdk version "25"`, you're done with
this step. If it says a lower number, or `java` isn't found, install Java 25 below.

## Install it

Any Java 25 build works. These steps use Eclipse Temurin, a free build from the Adoptium project.

=== "Windows"

    1. Go to [adoptium.net](https://adoptium.net/temurin/releases/?version=25) and pick **Windows**, **x64**,
       **JRE** (or JDK), version **25**.
    2. Download the `.msi` installer and run it. On the "Custom Setup" screen, turn on **Set JAVA_HOME** and
       **Add to PATH**.
    3. Close and reopen PowerShell, then run `java -version` again.

=== "macOS"

    1. Go to [adoptium.net](https://adoptium.net/temurin/releases/?version=25) and pick **macOS**, your chip
       (**aarch64** for Apple silicon, **x64** for Intel), version **25**.
    2. Download the `.pkg` installer and run it.
    3. Open a new Terminal window and run `java -version`.

    If you use Homebrew, `brew install --cask temurin@25` does the same.

=== "Linux"

    Many distributions package Java 25. On Debian and Ubuntu, try:

    ```bash
    sudo apt update
    sudo apt install openjdk-25-jre-headless
    ```

    If your release doesn't have it, add the Adoptium repository by following
    [Adoptium's Linux instructions](https://adoptium.net/installation/linux/), then install
    `temurin-25-jre`. Run `java -version` to check.

## Linux: check glibc

The bot's voice libraries need **glibc 2.38 or newer**. Check with:

```bash
ldd --version
```

The first line ends with the version, such as `2.39`. Ubuntu 24.04 and Debian 13 already have 2.38 or newer.
Older systems, such as Ubuntu 22.04 or Debian 12, don't, and the bot's voice libraries fail to load there.
On those, upgrade the system or use [Docker](docker.md), which brings its own.

On Debian and Ubuntu you may also need the audio libraries:

```bash
sudo apt install libopus0 libsodium23
```

Next: [Get a bot token](bot-token.md).
