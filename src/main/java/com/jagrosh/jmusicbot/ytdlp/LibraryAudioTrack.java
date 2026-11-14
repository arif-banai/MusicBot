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

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Supplier;

import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioReference;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.jetbrains.annotations.Nullable;

/**
 * A track that looks like the original YouTube track (same {@link AudioTrackInfo} and
 * source manager, so the queue, history and saved playlists record the YouTube URL)
 * but plays the yt-dlp library file for that video.
 * <p>
 * When it starts, it optionally waits for a {@code gate} (for example a user's
 * permission), then for the library file from {@code fileSource}, which may be a
 * download still in progress. It then plays the file by delegating to a local track,
 * so seeking works as for any local file. Waiting happens on the player thread; an
 * interrupt there means the track was stopped, and {@link #process} returns quietly.
 * <p>
 * User data is carried over by {@link #makeClone()}, as for every lavaplayer track.
 * When wrapping an existing track, the caller copies its user data with
 * {@link #setUserData(Object)}.
 *
 * @author Arif Banai (arif-banai)
 */
public final class LibraryAudioTrack extends DelegatedAudioTrack {

    /** Stateless apart from its container registry; {@code loadItem} never uses its manager argument. */
    private static final LocalAudioSourceManager LOCAL = new LocalAudioSourceManager();

    private final String videoId;
    private final Supplier<CompletableFuture<LibraryEntry>> fileSource;
    private final @Nullable CompletableFuture<Void> gate;
    private final @Nullable AudioSourceManager displaySource;

    /**
     * @param info          the original YouTube track's info, shown everywhere for this track
     * @param videoId       the YouTube video ID
     * @param fileSource    supplies the library entry, typically the downloader's shared
     *                      future for this video. It is asked only once the gate is open,
     *                      and its future is only ever waited on, never cancelled or
     *                      completed by this track.
     * @param gate          if not null, {@link #process} waits for it before asking for the file.
     *                      If it is cancelled or fails, the track ends quietly.
     * @param displaySource the original YouTube source manager, returned by {@link #getSourceManager()}
     */
    public LibraryAudioTrack(AudioTrackInfo info, String videoId,
                             Supplier<CompletableFuture<LibraryEntry>> fileSource,
                             @Nullable CompletableFuture<Void> gate,
                             @Nullable AudioSourceManager displaySource) {
        super(info);
        this.videoId = videoId;
        this.fileSource = fileSource;
        this.gate = gate;
        this.displaySource = displaySource;
    }

    public String getVideoId() {
        return videoId;
    }

    /** Whether this track was created with a gate, whether or not it has opened yet. */
    public boolean isGated() {
        return gate != null;
    }

    /** Whether this track has a gate that hasn't completed yet. */
    public boolean isAwaitingGate() {
        return gate != null && !gate.isDone();
    }

    @Override
    public void process(LocalAudioTrackExecutor executor) throws Exception {
        if (gate != null) {
            try {
                gate.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // stopped while waiting
                return;
            } catch (CancellationException | ExecutionException e) {
                return; // gate withdrawn: don't play
            }
        }

        LibraryEntry entry;
        try {
            // get(), not join(): join() ignores interrupts, so stop() couldn't release us.
            entry = fileSource.get().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // stopped while waiting
            return;
        } catch (CancellationException e) {
            return; // download withdrawn: guild stopped/left, or the bot is shutting down
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            throw new FriendlyException("yt-dlp couldn't download this track: " + cause.getMessage(),
                    Severity.COMMON, cause);
        }

        AudioItem item = LOCAL.loadItem(null, new AudioReference(entry.file().toString(), null));
        if (!(item instanceof InternalAudioTrack localTrack)) {
            throw new FriendlyException("The downloaded file for this track is missing or unreadable: "
                    + entry.file().getFileName(), Severity.COMMON, null);
        }
        processDelegate(localTrack, executor);
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new LibraryAudioTrack(trackInfo, videoId, fileSource, null, displaySource);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return displaySource;
    }
}
