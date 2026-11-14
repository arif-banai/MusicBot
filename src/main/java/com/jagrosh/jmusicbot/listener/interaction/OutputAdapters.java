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
package com.jagrosh.jmusicbot.listener.interaction;

import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.function.Consumer;

/**
 * Factory methods for MusicService.OutputAdapter used by interaction listeners.
 */
public final class OutputAdapters {

    private OutputAdapters() {
    }

    /**
     * Adapter for playback control buttons: replies and edits now-playing message.
     */
    public static MusicService.OutputAdapter forPlaybackButton(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        return new MusicService.OutputAdapter() {
            @Override
            public void replySuccess(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void replyError(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void replyWarning(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void editMessage(String content) {
                event.editMessage(content).queue();
            }

            @Override
            public void editMessage(String content, Consumer<net.dv8tion.jda.api.entities.Message> onSuccess) {
                event.editMessage(content).queue(hook -> hook.retrieveOriginal().queue(onSuccess));
            }

            @Override
            public void editNowPlaying(AudioHandler handler) {
                event.editMessage(asEditData(handler.getNowPlaying(event.getJDA()))).queue();
            }

            @Override
            public void editNoMusic(AudioHandler handler) {
                event.editMessage(asEditData(handler.getNoMusicPlaying(event.getJDA()))).queue();
            }

            @Override
            public void onShowHelp() {
            }
        };
    }

    /**
     * Adapter for favorite playback button:
     * success replies are public, warnings/errors remain ephemeral.
     */
    public static MusicService.OutputAdapter forFavoritePlaybackButton(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        return new MusicService.OutputAdapter() {
            private void sendResponse(String content, boolean ephemeral) {
                if (!event.isAcknowledged()) {
                    var reply = event.reply(content);
                    if (ephemeral) {
                        reply.setEphemeral(true);
                    }
                    reply.queue();
                    return;
                }

                var followup = event.getHook().sendMessage(content);
                if (ephemeral) {
                    followup.setEphemeral(true);
                }
                followup.queue();
            }

            @Override
            public void replySuccess(String content) {
                sendResponse(content, false);
            }

            @Override
            public void replyError(String content) {
                sendResponse(content, true);
            }

            @Override
            public void replyWarning(String content) {
                sendResponse(content, true);
            }

            @Override
            public void editMessage(String content) {
                event.editMessage(content).queue();
            }

            @Override
            public void editMessage(String content, Consumer<net.dv8tion.jda.api.entities.Message> onSuccess) {
                event.editMessage(content).queue(hook -> hook.retrieveOriginal().queue(onSuccess));
            }

            @Override
            public void editNowPlaying(AudioHandler handler) {
                event.editMessage(asEditData(handler.getNowPlaying(event.getJDA()))).queue();
            }

            @Override
            public void editNoMusic(AudioHandler handler) {
                event.editMessage(asEditData(handler.getNoMusicPlaying(event.getJDA()))).queue();
            }

            @Override
            public void onShowHelp() {
            }
        };
    }

    /**
     * Adapter for queue embed actions: only errors/warnings are replied; success updates the embed instead.
     */
    public static MusicService.OutputAdapter forQueueEmbed(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        return new MusicService.OutputAdapter() {
            @Override
            public void replySuccess(String content) {
            }

            @Override
            public void replyError(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void replyWarning(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void editMessage(String content) {
            }

            @Override
            public void editMessage(String content, Consumer<net.dv8tion.jda.api.entities.Message> onSuccess) {
            }

            @Override
            public void editNowPlaying(AudioHandler handler) {
            }

            @Override
            public void editNoMusic(AudioHandler handler) {
            }

            @Override
            public void onShowHelp() {
            }
        };
    }

    /**
     * Adapter for history embed actions: all feedback is ephemeral. It is a reply while the
     * interaction is unacknowledged, otherwise a follow-up through the interaction hook (the
     * listener defers the button before running the action, then refreshes the embed via the hook).
     */
    public static MusicService.OutputAdapter forHistoryReply(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        return new MusicService.OutputAdapter() {
            @Override
            public void replySuccess(String content) {
                sendEphemeral(event, content);
            }

            @Override
            public void replyError(String content) {
                sendEphemeral(event, content);
            }

            @Override
            public void replyWarning(String content) {
                sendEphemeral(event, content);
            }

            @Override
            public void editMessage(String content) {
            }

            @Override
            public void editMessage(String content, Consumer<net.dv8tion.jda.api.entities.Message> onSuccess) {
            }

            @Override
            public void editNowPlaying(AudioHandler handler) {
            }

            @Override
            public void editNoMusic(AudioHandler handler) {
            }

            @Override
            public void onShowHelp() {
            }
        };
    }

    /**
     * Adapter for playlists embed actions: all feedback is ephemeral. It is a reply while the
     * interaction is unacknowledged, otherwise a follow-up through the interaction hook, so a
     * playlist that finishes loading after the view was re-rendered does not reply a second time.
     */
    public static MusicService.OutputAdapter forPlaylistsReply(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        return new MusicService.OutputAdapter() {
            @Override
            public void replySuccess(String content) {
                sendEphemeral(event, content);
            }

            @Override
            public void replyError(String content) {
                sendEphemeral(event, content);
            }

            @Override
            public void replyWarning(String content) {
                sendEphemeral(event, content);
            }

            @Override
            public void editMessage(String content) {
            }

            @Override
            public void editMessage(String content, Consumer<net.dv8tion.jda.api.entities.Message> onSuccess) {
            }

            @Override
            public void editNowPlaying(AudioHandler handler) {
            }

            @Override
            public void editNoMusic(AudioHandler handler) {
            }

            @Override
            public void onShowHelp() {
            }
        };
    }

    /**
     * Adapter for the library panel's Play/Queue buttons: all feedback is ephemeral. It is a
     * reply while the interaction is unacknowledged, otherwise a follow-up through the
     * interaction hook (the listener defers the button before running the action). The load
     * handlers report through {@code editMessage} ("Added ... to the queue", "No results"), so
     * those become ephemeral follow-ups too, leaving the panel message itself alone.
     */
    public static MusicService.OutputAdapter forLibraryReply(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent event) {
        return new MusicService.OutputAdapter() {
            @Override
            public void replySuccess(String content) {
                sendEphemeral(event, content);
            }

            @Override
            public void replyError(String content) {
                sendEphemeral(event, content);
            }

            @Override
            public void replyWarning(String content) {
                sendEphemeral(event, content);
            }

            @Override
            public void editMessage(String content) {
                sendEphemeral(event, content);
            }

            @Override
            public void editMessage(String content, Consumer<net.dv8tion.jda.api.entities.Message> onSuccess) {
                if (event.isAcknowledged()) {
                    event.getHook().sendMessage(content).setEphemeral(true).queue(onSuccess);
                    return;
                }
                event.reply(content).setEphemeral(true).queue(hook -> hook.retrieveOriginal().queue(onSuccess));
            }

            @Override
            public void editNowPlaying(AudioHandler handler) {
            }

            @Override
            public void editNoMusic(AudioHandler handler) {
            }

            @Override
            public void onShowHelp() {
            }
        };
    }

    /**
     * Adapter for queue move select: only errors/warnings replied.
     */
    public static MusicService.OutputAdapter forQueueMoveSelect(net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent event) {
        return new MusicService.OutputAdapter() {
            @Override
            public void replySuccess(String content) {
            }

            @Override
            public void replyError(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void replyWarning(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void editMessage(String content) {
            }

            @Override
            public void editMessage(String content, Consumer<net.dv8tion.jda.api.entities.Message> onSuccess) {
            }

            @Override
            public void editNowPlaying(AudioHandler handler) {
            }

            @Override
            public void editNoMusic(AudioHandler handler) {
            }

            @Override
            public void onShowHelp() {
            }
        };
    }

    /**
     * Adapter for history save modal: all feedback via ephemeral reply.
     */
    public static MusicService.OutputAdapter forHistoryModal(net.dv8tion.jda.api.events.interaction.ModalInteractionEvent event) {
        return new MusicService.OutputAdapter() {
            @Override
            public void replySuccess(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void replyError(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void replyWarning(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void editMessage(String content) {
            }

            @Override
            public void editMessage(String content, Consumer<net.dv8tion.jda.api.entities.Message> onSuccess) {
            }

            @Override
            public void editNowPlaying(AudioHandler handler) {
            }

            @Override
            public void editNoMusic(AudioHandler handler) {
            }

            @Override
            public void onShowHelp() {
            }
        };
    }

    /**
     * Sends ephemeral feedback for an interaction: replies while the interaction is
     * unacknowledged, otherwise sends a follow-up through its hook (an interaction can only be
     * acknowledged once; a second reply fails with IllegalStateException).
     */
    private static void sendEphemeral(IReplyCallback event, String content) {
        if (event.isAcknowledged()) {
            event.getHook().sendMessage(content).setEphemeral(true).queue();
            return;
        }
        event.reply(content).setEphemeral(true).queue();
    }

    private static MessageEditData asEditData(MessageCreateData source) {
        return new MessageEditBuilder()
                .setContent(source.getContent() == null ? "" : source.getContent())
                .setEmbeds(source.getEmbeds())
                .setComponents(source.getComponents())
                .build();
    }
}
