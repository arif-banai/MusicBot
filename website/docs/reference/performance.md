---
description: Java flags and buffer settings for smooth audio.
---

# Performance tuning

The defaults work for most servers. Read this page if the music stutters or cuts out for a moment now and then,
especially on a small VPS or a busy computer.

Short stutters usually come from Java pausing to clean up memory. The fix is a garbage collector with very short
pauses, plus enough audio buffered ahead to cover any pause that still happens.

## Java flags

Start the bot with ZGC, Java's low-pause garbage collector:

```bash
java -Dfile.encoding=UTF-8 \
     -XX:+UseZGC \
     -XX:+AlwaysPreTouch \
     -Dnogui=true \
     --enable-native-access=ALL-UNNAMED \
     -jar JMusicBot-0.9.1.jar
```

| Flag | What it does |
|---|---|
| `-XX:+UseZGC` | Uses ZGC, whose pauses are well under a millisecond. |
| `-XX:+AlwaysPreTouch` | Sets up all memory at startup, so the bot doesn't stall to grab more later. |
| `-Xms` / `-Xmx` | Optional. Fixes or limits memory, for example `-Xms256m -Xmx512m`. |
| `-Dfile.encoding=UTF-8` | Shows non-English song titles correctly. |
| `--enable-native-access=ALL-UNNAMED` | Lets the audio libraries load without warnings. |

On Windows PowerShell, quote each `-D` option, for example `"-Dfile.encoding=UTF-8"`. See
[First run](../setup/first-run.md).

### Docker

The Docker image already uses ZGC and AlwaysPreTouch. To add a memory limit, set `JAVA_OPTS`:

```yaml
environment:
  - JAVA_OPTS=-Xms256m -Xmx512m -XX:+UseZGC -XX:+AlwaysPreTouch
```

## Audio buffers

Two settings in `config.txt` control how much audio the bot keeps ready:

```hocon
performance {
  nasBufferMs = 800      # audio ready to send; covers Java pauses up to this long
  frameBufferMs = 2000   # decoded audio kept ahead of playback
}
```

Raise them if you still hear stutters after switching to ZGC. Higher values add a little delay when you skip,
pause or change the volume. The defaults suit most setups.

## Other things to check

- **Memory.** If the computer is low on memory, other programs can push the bot into swap, which causes long
  stalls. Give it a few hundred MB to itself.
- **CPU.** A shared VPS with "burstable" CPU can run out of credits and slow down. Check your provider's dashboard
  if stutters come at busy times.
- **Network.** Stutters on every song, from every source, can be a slow or unstable connection on the bot's side.

Still stuttering? See [Troubleshooting](../help/troubleshooting.md).
