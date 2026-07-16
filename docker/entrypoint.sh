#!/bin/sh
set -eu

JAR="/app/app.jar"

if [ ! -f "$JAR" ]; then
  echo "[ERROR] JAR file not found at /app/app.jar"
  exit 1
fi

echo "[INFO] ========================================"
echo "[INFO] JMusicBot Containerized"
echo "[INFO] ========================================"
echo "[INFO] Selected jar: $JAR"
echo "[INFO] Working directory: $(pwd)"

if [ ! -f "config.txt" ]; then
  echo "[INFO] config.txt: Not found - generating default config"

  # Safe to clean: no config exists = no other instance can be running.
  # Prevents stale lock from blocking restarts on FUSE filesystems (Unraid).
  rm -f .jmusicbot.lock

  java -Dnogui=true --enable-native-access=ALL-UNNAMED -jar "$JAR" generate-config
  echo "[INFO] ========================================"
  echo "[INFO] Default config.txt created from reference.conf"
  echo "[INFO] 1. Edit config.txt and set your bot token"
  echo "[INFO] 2. Set your owner ID (Discord user ID)"
  echo "[INFO] 3. Restart the container"
  echo "[INFO] ========================================"
  exit 0
fi

echo "[INFO] config.txt: Found (existing)"
echo "[INFO] ========================================"

: "${JAVA_OPTS:=-XX:+UseZGC -XX:+AlwaysPreTouch}"

set -- java -Dnogui=true --enable-native-access=ALL-UNNAMED

if [ -n "${JAVA_OPTS:-}" ]; then
  set -- "$@" $JAVA_OPTS
fi

set -- "$@" -jar "$JAR"

exec "$@"
