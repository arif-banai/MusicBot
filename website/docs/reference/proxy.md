---
description: Send some or all of the bot's traffic through an HTTP proxy.
---

# Proxy

The bot can send its traffic through an HTTP proxy. You choose which parts go through it: music requests, Discord
traffic and the update check are separate switches, so you can proxy only what you need.

Most people don't need a proxy. Set one up if the computer running the bot can only reach the internet through a
proxy, or if music requests fail from where the bot runs while Discord works fine.

!!! note "HTTP proxies only"
    SOCKS proxies aren't supported. Proxy support is still new, so tell us in the
    [discussions](https://github.com/arif-banai/MusicBot/discussions) if something doesn't work.

## Settings

Add a `proxy` block to `config.txt`:

```hocon
proxy {
  host = "127.0.0.1"   # proxy address; leave empty for no proxy
  port = 8080          # proxy port

  lavaplayer = true    # music requests: YouTube, SoundCloud and other sources
  jda = false          # Discord traffic
  github = false       # the new-version check
}
```

Leave `host` empty, or `port` at `0`, to turn the proxy off. Each switch works on its own. The full list is in the
[config.txt reference](config.md#proxy).

## Examples

**Only music through the proxy.** The most common setup: Discord connects directly and only music requests use
the proxy.

```hocon
proxy {
  host = "127.0.0.1"
  port = 18080
  lavaplayer = true
  jda = false
  github = false
}
```

**Everything through the proxy.** For a computer that can only reach the internet through a proxy.

```hocon
proxy {
  host = "proxy.example.com"
  port = 8080
  lavaplayer = true
  jda = true
  github = true
}
```

## Using Java's proxy settings instead

You can also set a proxy for the whole Java process on the command line. This applies to everything the bot does:

```bash
java -Dhttp.proxyHost=proxy.example.com -Dhttp.proxyPort=8080 \
     -Dhttps.proxyHost=proxy.example.com -Dhttps.proxyPort=8080 \
     -jar JMusicBot-0.9.1.jar
```

Restart the bot after changing any proxy setting.
