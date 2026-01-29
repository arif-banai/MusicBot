#!/bin/sh

# This will have this script check for a new version of JMusicBot every
# startup (and download it if the latest version isn't currently downloaded)
DOWNLOAD=true

# This will cause the script to run in a loop so that the bot auto-restarts
# when you use the shutdown command
LOOP=true

download() {
    if [ $DOWNLOAD = true ]; then
        # First, check if the latest release is a pre-release
        LATEST_RELEASE=$(curl -s https://api.github.com/repos/arif-banai/MusicBot/releases/latest)
        IS_PRERELEASE=$(echo "$LATEST_RELEASE" | grep -o '"prerelease":[^,}]*' | head -1 | grep -o 'true')
        
        if [ "$IS_PRERELEASE" = "true" ]; then
            # Latest is a pre-release, so get all releases and find the first non-prerelease
            ALL_RELEASES=$(curl -s https://api.github.com/repos/arif-banai/MusicBot/releases?per_page=10)
            # Find the first release block that has "prerelease": false, then extract JAR URL from it
            # This handles the JSON structure where assets are nested
            URL=$(echo "$ALL_RELEASES" | awk '
                BEGIN { in_non_prerelease = 0; found = 0 }
                /"prerelease":\s*false/ { in_non_prerelease = 1 }
                /"prerelease":\s*true/ { in_non_prerelease = 0 }
                in_non_prerelease && /"browser_download_url".*\.jar/ && !found {
                    match($0, /"browser_download_url":\s*"([^"]+\.jar)"/, arr)
                    if (arr[1] != "") {
                        print arr[1]
                        found = 1
                        exit
                    }
                }
            ')
        else
            # Latest is not a pre-release, use it
            URL=$(echo "$LATEST_RELEASE" | grep -i "browser_download_url.*\.jar" | sed 's/.*\(http.*\)"/\1/')
        fi
        
        FILENAME=$(echo $URL | sed 's/.*\/\([^\/]*\)/\1/')
        if [ -f $FILENAME ]; then
            echo "Latest version already downloaded (${FILENAME})"
        else
            curl -L $URL -o $FILENAME
        fi
    fi
}

run() {
    # JVM flags for optimal audio performance:
    # -Xms512m -Xmx512m: Fixed heap size prevents resizing pauses
    # -XX:+UseZGC -XX:+ZGenerational: Sub-millisecond GC pauses (Java 21+)
    # -XX:+AlwaysPreTouch: Pre-allocate memory at startup to avoid page faults
    java -Xms512m -Xmx512m \
         -XX:+UseZGC -XX:+ZGenerational \
         -XX:+AlwaysPreTouch \
         -Dnogui=true \
         --enable-native-access=ALL-UNNAMED \
         -jar $(ls -t JMusicBot* | head -1)
}

while
    download
    run
    $LOOP
do
    continue
done 
