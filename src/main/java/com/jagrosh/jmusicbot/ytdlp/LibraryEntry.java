/*
 * Copyright 2026 Arif Banai (arif-banai)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.ytdlp;

import java.nio.file.Path;

import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

/**
 * A single entry in the yt-dlp download library's index: a downloaded file and the
 * YouTube metadata that was known for it (or guessed from its filename, when it was
 * adopted without an index entry).
 *
 * @author Arif Banai (arif-banai)
 */
public record LibraryEntry(String videoId, Path file, String title, String author,
                            long lengthMs, String artworkUrl, String uri) {

    /**
     * Rebuilds an {@link AudioTrackInfo} as if this were the original YouTube track,
     * so library hits look identical to YouTube tracks everywhere the bot shows them.
     * The identifier is this entry's {@link #videoId()} and the track is never a stream.
     */
    public AudioTrackInfo toTrackInfo() {
        return new AudioTrackInfo(title, author, lengthMs, videoId, false, uri, artworkUrl, null);
    }
}
