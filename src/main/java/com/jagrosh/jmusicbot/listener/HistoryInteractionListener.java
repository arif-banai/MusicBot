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

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.music.HistorySlashCmd;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.listener.interaction.InteractionGuards;
import com.jagrosh.jmusicbot.listener.interaction.OutputAdapters;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

/**
 * Handles history embed button and modal interactions (history_*, history_save_*).
 */
public class HistoryInteractionListener extends ListenerAdapter {

    private final Bot bot;

    public HistoryInteractionListener(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("history_")) {
            return;
        }
        handleHistoryButton(event);
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (!event.getModalId().startsWith("history_save_")) {
            return;
        }
        handleHistorySaveModal(event);
    }

    private void handleHistoryButton(ButtonInteractionEvent event) {
        if (!InteractionGuards.requireGuildAndMember(event)) {
            return;
        }

        Optional<ComponentIdParsers.PaginatedButtonId> parsed = ComponentIdParsers.parseHistoryButtonId(event.getComponentId());
        if (parsed.isEmpty()) {
            event.reply("Invalid button state.").setEphemeral(true).queue();
            return;
        }
        ComponentIdParsers.PaginatedButtonId id = parsed.get();

        if (event.getUser().getIdLong() != id.userId()) {
            event.reply("Only the user who ran the command can use these buttons!").setEphemeral(true).queue();
            return;
        }

        int page = id.page();
        int selectedTrack = id.selectedTrack();
        long userId = id.userId();
        String action = id.action();

        MusicService musicService = bot.getMusicService();
        MusicService.HistoryInfo historyInfo = musicService.getHistoryInfo(event.getGuild(), event.getJDA());

        if (historyInfo == null) {
            event.editMessage("Playback history is now empty!").setEmbeds().setComponents().queue();
            return;
        }
        if (historyInfo.isDisabled()) {
            event.editMessage(MusicService.HISTORY_DISABLED_MESSAGE).setEmbeds().setComponents().queue();
            return;
        }
        if (historyInfo.isEmpty()) {
            event.editMessage("Playback history is now empty!").setEmbeds().setComponents().queue();
            return;
        }

        int totalPages = HistorySlashCmd.getTotalPages(historyInfo.tracks.length);
        page = Math.max(1, Math.min(page, totalPages));

        if (action.startsWith("select")) {
            int newSelectedTrack;
            try {
                newSelectedTrack = Integer.parseInt(action.substring(6));
            } catch (NumberFormatException e) {
                event.reply("Invalid track selection.").setEphemeral(true).queue();
                return;
            }
            int tracksOnPage = HistorySlashCmd.getTracksOnPage(page, historyInfo.tracks.length);
            int firstOnPage = (page - 1) * HistorySlashCmd.TRACKS_PER_PAGE + 1;
            int lastOnPage = firstOnPage + tracksOnPage - 1;
            if (newSelectedTrack < firstOnPage || newSelectedTrack > lastOnPage) {
                event.reply("That track isn't on this page!").setEphemeral(true).queue();
                return;
            }
            if (newSelectedTrack == selectedTrack) {
                newSelectedTrack = 0;
            }
            if (newSelectedTrack > historyInfo.tracks.length) {
                event.reply("That track doesn't exist!").setEphemeral(true).queue();
                return;
            }
            updateHistoryEmbed(event, historyInfo, page, totalPages, newSelectedTrack, userId);
        } else if (action.equals("prev")) {
            int newPage = Math.max(1, page - 1);
            updateHistoryEmbed(event, historyInfo, newPage, totalPages, 0, userId);
        } else if (action.equals("next")) {
            int newPage = Math.min(totalPages, page + 1);
            updateHistoryEmbed(event, historyInfo, newPage, totalPages, 0, userId);
        } else if (action.equals("queue")) {
            if (selectedTrack <= 0 || selectedTrack > historyInfo.tracks.length) {
                event.reply("No track selected!").setEphemeral(true).queue();
                return;
            }
            if (!InteractionGuards.ensureBotInUserVoiceChannel(event, bot)) {
                return;
            }
            MusicService.OutputAdapter adapter = OutputAdapters.forHistoryReply(event);
            GuildMessageChannel channel = event.getChannel().asGuildMessageChannel();
            musicService.queueFromHistory(event.getGuild(), event.getMember(), selectedTrack, channel, adapter);
            MusicService.HistoryInfo newInfo = musicService.getHistoryInfo(event.getGuild(), event.getJDA());
            if (newInfo == null) {
                event.editMessage("Playback history is now empty!").setEmbeds().setComponents().queue();
            } else if (newInfo.isDisabled()) {
                event.editMessage(MusicService.HISTORY_DISABLED_MESSAGE).setEmbeds().setComponents().queue();
            } else if (newInfo.isEmpty()) {
                event.editMessage("Playback history is now empty!").setEmbeds().setComponents().queue();
            } else {
                int newTotalPages = HistorySlashCmd.getTotalPages(newInfo.tracks.length);
                int safePage = Math.min(page, newTotalPages);
                updateHistoryEmbed(event, newInfo, safePage, newTotalPages, 0, userId);
            }
        } else if (action.equals("playnow")) {
            if (selectedTrack <= 0 || selectedTrack > historyInfo.tracks.length) {
                event.reply("No track selected!").setEphemeral(true).queue();
                return;
            }
            if (!InteractionGuards.ensureBotInUserVoiceChannel(event, bot)) {
                return;
            }
            MusicService.OutputAdapter adapter = OutputAdapters.forHistoryReply(event);
            GuildMessageChannel channel = event.getChannel().asGuildMessageChannel();
            musicService.playFromHistoryNow(event.getGuild(), event.getMember(), selectedTrack, channel, adapter);
            MusicService.HistoryInfo newInfo = musicService.getHistoryInfo(event.getGuild(), event.getJDA());
            if (newInfo == null) {
                event.editMessage("Playback history is now empty!").setEmbeds().setComponents().queue();
            } else if (newInfo.isDisabled()) {
                event.editMessage(MusicService.HISTORY_DISABLED_MESSAGE).setEmbeds().setComponents().queue();
            } else if (newInfo.isEmpty()) {
                event.editMessage("Playback history is now empty!").setEmbeds().setComponents().queue();
            } else {
                int newTotalPages = HistorySlashCmd.getTotalPages(newInfo.tracks.length);
                updateHistoryEmbed(event, newInfo, 1, newTotalPages, 0, userId);
            }
        } else if (action.equals("queueall")) {
            if (!InteractionGuards.ensureBotInUserVoiceChannel(event, bot)) {
                return;
            }
            MusicService.OutputAdapter adapter = OutputAdapters.forHistoryReply(event);
            GuildMessageChannel channel = event.getChannel().asGuildMessageChannel();
            musicService.queueAllFromHistory(event.getGuild(), event.getMember(), channel, adapter);
            MusicService.HistoryInfo newInfo = musicService.getHistoryInfo(event.getGuild(), event.getJDA());
            if (newInfo == null) {
                event.editMessage("Playback history is now empty!").setEmbeds().setComponents().queue();
            } else if (newInfo.isDisabled()) {
                event.editMessage(MusicService.HISTORY_DISABLED_MESSAGE).setEmbeds().setComponents().queue();
            } else if (newInfo.isEmpty()) {
                event.editMessage("Playback history is now empty!").setEmbeds().setComponents().queue();
            } else {
                int newTotalPages = HistorySlashCmd.getTotalPages(newInfo.tracks.length);
                int safePage = Math.min(page, newTotalPages);
                updateHistoryEmbed(event, newInfo, safePage, newTotalPages, 0, userId);
            }
        } else if (action.equals("save")) {
            TextInput input = TextInput.create("playlist_name", TextInputStyle.SHORT)
                    .setPlaceholder("e.g. my-history")
                    .setMinLength(1)
                    .setMaxLength(100)
                    .setRequired(true)
                    .build();
            Modal modal = Modal.create("history_save_" + userId, "Save history as playlist")
                    .addComponents(Label.of("Playlist name", input))
                    .build();
            event.replyModal(modal).queue();
        }
    }

    private void handleHistorySaveModal(ModalInteractionEvent event) {
        if (!InteractionGuards.requireGuildAndMember(event)) {
            return;
        }

        Optional<Long> userIdOpt = ComponentIdParsers.parseHistorySaveModalUserId(event.getModalId());
        if (userIdOpt.isEmpty()) {
            event.reply("Invalid modal state.").setEphemeral(true).queue();
            return;
        }
        long userId = userIdOpt.get();

        if (event.getUser().getIdLong() != userId) {
            event.reply("Only the user who opened the save dialog can submit it!").setEphemeral(true).queue();
            return;
        }

        String playlistName = event.getValues().stream()
                .filter(m -> "playlist_name".equals(m.getCustomId()))
                .findFirst()
                .map(net.dv8tion.jda.api.interactions.modals.ModalMapping::getAsString)
                .orElse("")
                .trim();

        if (playlistName.isEmpty()) {
            event.reply("Please enter a playlist name!").setEphemeral(true).queue();
            return;
        }

        MusicService.OutputAdapter adapter = OutputAdapters.forHistoryModal(event);

        bot.getMusicService().saveHistoryAsPlaylist(event.getGuild(), event.getMember(), playlistName, adapter);
    }

    private void updateHistoryEmbed(ButtonInteractionEvent event, MusicService.HistoryInfo historyInfo,
                                    int page, int totalPages, int selectedTrack, long userId) {
        int tracksOnPage = HistorySlashCmd.getTracksOnPage(page, historyInfo.tracks.length);
        MessageEmbed embed = HistorySlashCmd.buildHistoryEmbed(historyInfo, page, totalPages, selectedTrack,
                event.getMember().getColor());
        List<ActionRow> components = HistorySlashCmd.buildHistoryComponents(page, totalPages, tracksOnPage, selectedTrack, userId);
        event.editMessageEmbeds(embed).setComponents(components).queue();
    }
}
