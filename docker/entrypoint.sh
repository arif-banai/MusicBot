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
if [ -f "config.txt" ]; then
  echo "[INFO] config.txt: Found (existing)"
else
  echo "[INFO] config.txt: Not found (will be generated on first run)"
fi
echo "[INFO] ========================================"

# Default JVM options for optimal audio performance (ZGC with sub-ms pauses)
# Users can override by setting JAVA_OPTS environment variable
: "${JAVA_OPTS:=-Xms512m -Xmx512m -XX:+UseZGC -XX:+ZGenerational -XX:+AlwaysPreTouch}"

# Build argv
set -- java -Dnogui=true --enable-native-access=ALL-UNNAMED

# Append JAVA_OPTS (either default or user-provided)
if [ -n "${JAVA_OPTS:-}" ]; then
  # shellcheck disable=SC2086
  set -- "$@" $JAVA_OPTS
fi

set -- "$@" -jar "$JAR"

exec "$@"
