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
package com.jagrosh.jmusicbot.listener;

import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.listener.interaction.InteractionGuards;
import com.jagrosh.jmusicbot.listener.interaction.OutputAdapters;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.ytdlp.LibraryAudioTrack;
import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.LibraryKeys;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.MarkdownSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles playback control button interactions (stop, pause, skip, previous, shuffle, repeat, favorite, volume)
 * and the yt-dlp "Download" button ({@code np_ytdlp_save}).
 */
public class PlaybackControlsListener extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaybackControlsListener.class);

    private final Bot bot;

    public PlaybackControlsListener(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (ComponentIdParsers.NOW_PLAYING_YTDLP_SAVE.equals(event.getComponentId())) {
            handleDownloadButton(event);
            return;
        }

        var parsedId = ComponentIdParsers.parseNowPlayingButtonId(event.getComponentId());
        if (parsedId.isEmpty()) {
            return;
        }

        if (event.getGuild() == null || event.getMember() == null) {
            return;
        }

        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler == null) {
            event.reply("There is no music playing!").setEphemeral(true).queue();
            return;
        }

        if (!event.getMember().getVoiceState().inAudioChannel()
                || !event.getMember().getVoiceState().getChannel().equals(event.getGuild().getSelfMember().getVoiceState().getChannel())) {
            event.reply("You must be in the same voice channel to use this!").setEphemeral(true).queue();
            return;
        }

        MusicService musicService = bot.getMusicService();

        switch (parsedId.get().action()) {
            case "previous":
                musicService.previous(event.getGuild(), event.getMember(),
                        suppressSuccess(OutputAdapters.forPlaybackButton(event)));
                break;
            case "shuffle":
                musicService.shuffle(event.getGuild(), event.getMember(), 0, OutputAdapters.forPlaybackButton(event));
                break;
            case "repeat":
                musicService.cycleRepeatMode(event.getGuild(), event.getMember(), OutputAdapters.forPlaybackButton(event));
                break;
            case "favorite":
                musicService.addCurrentTrackToFavorites(event.getGuild(), event.getMember(), OutputAdapters.forFavoritePlaybackButton(event));
                break;
            case "voldown":
                musicService.adjustVolume(event.getGuild(), event.getMember(), -10, OutputAdapters.forPlaybackButton(event));
                break;
            case "volup":
                musicService.adjustVolume(event.getGuild(), event.getMember(), 10, OutputAdapters.forPlaybackButton(event));
                break;
            case "stop":
                musicService.stop(event.getGuild(), event.getMember(), OutputAdapters.forPlaybackButton(event));
                break;
            case "pause":
                musicService.pause(event.getGuild(), event.getMember(), OutputAdapters.forPlaybackButton(event));
                break;
            case "skip":
                musicService.skip(event.getGuild(), event.getMember(),
                        suppressSuccess(OutputAdapters.forPlaybackButton(event)));
                break;
            default:
                break;
        }
    }

    private static MusicService.OutputAdapter suppressSuccess(MusicService.OutputAdapter delegate) {
        return new MusicService.OutputAdapter() {
            @Override
            public void replySuccess(String content) {
                // Intentionally silent for targeted now-playing actions.
            }

            @Override
            public void replyError(String content) {
                delegate.replyError(content);
            }

            @Override
            public void replyWarning(String content) {
                delegate.replyWarning(content);
            }

            @Override
            public void editMessage(String content) {
                delegate.editMessage(content);
            }

            @Override
            public void editMessage(String content, java.util.function.Consumer<net.dv8tion.jda.api.entities.Message> onSuccess) {
                delegate.editMessage(content, onSuccess);
            }

            @Override
            public void editNowPlaying(AudioHandler handler) {
                delegate.editNowPlaying(handler);
            }

            @Override
            public void editNoMusic(AudioHandler handler) {
                delegate.editNoMusic(handler);
            }

            @Override
            public void onShowHelp() {
                delegate.onShowHelp();
            }
        };
    }

    /**
     * Handles the yt-dlp "Download" ({@code np_ytdlp_save}) button: a DJ-only action that saves
     * the currently playing song into the yt-dlp library without touching playback. Acknowledges
     * the interaction exactly once with an ephemeral {@code deferReply}, then edits that reply's
     * hook as the download progresses. Never cancels or completes the downloader's shared future;
     * every Discord call is {@code .queue()}d, never awaited.
     */
    private void handleDownloadButton(ButtonInteractionEvent event) {
        if (!InteractionGuards.requireGuildAndMember(event)) {
            return;
        }
        if (!DJCommand.checkDJPermission(bot, event.getGuild(), event.getMember())) {
            event.reply("Only a DJ can use this button.").setEphemeral(true).queue();
            return;
        }

        YtDlpService ytDlp = bot.getYtDlp();
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        AudioTrack track = handler == null ? null : handler.getPlayer().getPlayingTrack();
        Optional<String> key = track == null ? Optional.empty() : LibraryKeys.of(track);
        boolean downloadable = ytDlp != null && ytDlp.isEnabled() && track != null
                && !(track instanceof LibraryAudioTrack) && key.isPresent()
                && ytDlp.library().resolve(key.get()).isEmpty();
        if (!downloadable) {
            event.reply("This song can't be downloaded.").setEphemeral(true).queue();
            return;
        }

        long guildId = event.getGuild().getIdLong();
        String title = MarkdownSanitizer.escape(FormatUtil.filter(FormatUtil.getTrackTitle(track)));

        event.deferReply(true).queue(hook -> {
            CompletableFuture<LibraryEntry> download = ytDlp.requestDownload(track, guildId, false);
            hook.editOriginal("⏳ Downloading **" + title + "**…").queue(null, error -> { });
            download.whenComplete((entry, error) -> onDownloadComplete(hook, guildId, title, error));
        }, error -> LOGGER.debug("Couldn't acknowledge the yt-dlp download button: {}", error.toString()));
    }

    private void onDownloadComplete(InteractionHook hook, long guildId, String title, Throwable error) {
        if (error == null) {
            hook.editOriginal("✅ Saved **" + title + "** to the library").queue(null, err -> { });
            try {
                bot.getNowplayingHandler().refreshNowPlaying(guildId);
            } catch (RuntimeException ex) {
                LOGGER.warn("Couldn't refresh now-playing after a yt-dlp download in guild {}: {}", guildId,
                        ex.toString());
            }
        } else if (unwrap(error) instanceof CancellationException) {
            // Withdrawn by /stop, Play now or leaving voice (YtDlpDownloader.cancelGuild).
            hook.editOriginal("Download cancelled.").queue(null, err -> { });
        } else {
            hook.editOriginal("❌ yt-dlp failed: " + failureReason(error)).queue(null, err -> { });
        }
    }

    /** The failure itself, unwrapping a {@link CompletionException} if present. */
    private static Throwable unwrap(Throwable error) {
        return error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
    }

    /** The yt-dlp failure's own message, unwrapping a {@link CompletionException} if present. */
    private static String failureReason(Throwable error) {
        Throwable cause = unwrap(error);
        String reason = cause.getMessage() != null ? cause.getMessage() : cause.toString();
        return FormatUtil.filter(reason.strip());
    }
}
