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
import com.jagrosh.jmusicbot.commands.v2.music.PlaylistsSlashCmd;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.listener.interaction.InteractionGuards;
import com.jagrosh.jmusicbot.listener.interaction.OutputAdapters;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Handles playlists embed button interactions (playlists_*).
 */
public class PlaylistsInteractionListener extends ListenerAdapter
{
    private final Bot bot;

    public PlaylistsInteractionListener(Bot bot)
    {
        this.bot = bot;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event)
    {
        String componentId = event.getComponentId();
        if (componentId.startsWith("playlists_"))
        {
            handlePlaylistsButton(event);
            return;
        }
        if (componentId.startsWith("playlistdetails_"))
        {
            handlePlaylistDetailsButton(event);
            return;
        }
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event)
    {
        if (!event.getComponentId().startsWith("playlistdetails_move_select_"))
        {
            return;
        }
        handlePlaylistDetailsMoveSelect(event);
    }

    private void handlePlaylistDetailsButton(ButtonInteractionEvent event)
    {
        if (!InteractionGuards.requireGuildAndMember(event))
        {
            return;
        }

        Optional<ComponentIdParsers.PlaylistDetailsBackId> parsedBack =
                ComponentIdParsers.parsePlaylistDetailsBackId(event.getComponentId());
        if (parsedBack.isPresent())
        {
            ComponentIdParsers.PlaylistDetailsBackId backId = parsedBack.get();
            if (event.getUser().getIdLong() != backId.userId())
            {
                event.reply("Only the user who ran the command can use these buttons!").setEphemeral(true).queue();
                return;
            }
            MusicService.PlaylistNamesInfo namesInfo = loadPlaylistNames();
            if (!namesInfo.hasError()
                    && backId.listSelectedIndex() > 0
                    && backId.listSelectedIndex() <= namesInfo.names.size())
            {
                String playlistName = namesInfo.names.get(backId.listSelectedIndex() - 1);
                MusicService.PlaylistDraftContext draftContext = buildDraftContext(event, backId.userId(), playlistName);
                if (bot.getMusicService().isPlaylistDraftDirty(draftContext))
                {
                    bot.getMusicService().discardPlaylistDraft(draftContext);
                }
            }
            renderPlaylistListView(event, backId.listPage(), backId.listSelectedIndex(), backId.userId());
            return;
        }

        Optional<ComponentIdParsers.PlaylistDetailsButtonId> parsed =
                ComponentIdParsers.parsePlaylistDetailsButtonId(event.getComponentId());
        if (parsed.isEmpty())
        {
            event.reply("Invalid button state.").setEphemeral(true).queue();
            return;
        }
        ComponentIdParsers.PlaylistDetailsButtonId id = parsed.get();
        if (event.getUser().getIdLong() != id.userId())
        {
            event.reply("Only the user who ran the command can use these buttons!").setEphemeral(true).queue();
            return;
        }

        MusicService.PlaylistNamesInfo playlistNamesInfo = loadPlaylistNames();
        if (playlistNamesInfo.hasError())
        {
            event.reply(playlistNamesInfo.errorMessage).setEphemeral(true).queue();
            return;
        }
        List<String> playlists = playlistNamesInfo.names;
        if (id.playlistIndex() <= 0 || id.playlistIndex() > playlists.size())
        {
            event.reply("Playlist no longer exists. Click Back and Refresh.").setEphemeral(true).queue();
            return;
        }

        String playlistName = playlists.get(id.playlistIndex() - 1);
        MusicService.PlaylistDetailsInfo detailsInfo = bot.getMusicService().getPlaylistDetails(playlistName);
        if (detailsInfo == null)
        {
            event.reply("Playlist no longer exists. Click Back and Refresh.").setEphemeral(true).queue();
            return;
        }

        MusicService.PlaylistDraftContext draftContext = buildDraftContext(event, id.userId(), playlistName);
        int totalItems = bot.getMusicService().getPlaylistTrackCount(draftContext);
        if (totalItems < 0)
        {
            event.reply("Playlist no longer exists. Click Back and Refresh.").setEphemeral(true).queue();
            return;
        }
        boolean canEditPlaylist = bot.getMusicService().canEditPlaylistEntries(event.getGuild(), event.getMember());
        int totalPages = Math.max(1, PlaylistsSlashCmd.getPlaylistTrackTotalPages(totalItems));
        int page = Math.max(1, Math.min(id.detailsPage(), totalPages));
        int selectedTrack = id.selectedTrack();
        String action = id.action();

        if (action.startsWith("select"))
        {
            int newSelectedTrack;
            try
            {
                newSelectedTrack = Integer.parseInt(action.substring(6));
            }
            catch (NumberFormatException e)
            {
                event.reply("Invalid track selection.").setEphemeral(true).queue();
                return;
            }
            int tracksOnPage = PlaylistsSlashCmd.getPlaylistTracksOnPage(page, totalItems);
            int firstOnPage = (page - 1) * PlaylistsSlashCmd.PLAYLIST_TRACKS_PER_PAGE + 1;
            int lastOnPage = firstOnPage + tracksOnPage - 1;
            if (newSelectedTrack < firstOnPage || newSelectedTrack > lastOnPage)
            {
                event.reply("That track isn't on this page!").setEphemeral(true).queue();
                return;
            }
            if (newSelectedTrack == selectedTrack)
            {
                newSelectedTrack = 0;
            }
            if (newSelectedTrack > totalItems)
            {
                event.reply("That track doesn't exist!").setEphemeral(true).queue();
                return;
            }
            renderPlaylistDetailsView(event, id.playlistIndex(), id.listPage(), page, newSelectedTrack, id.userId(), playlistName, draftContext);
            return;
        }

        if (action.equals("prev"))
        {
            int newPage = Math.max(1, page - 1);
            renderPlaylistDetailsView(event, id.playlistIndex(), id.listPage(), newPage, 0, id.userId(), playlistName, draftContext);
            return;
        }
        if (action.equals("next"))
        {
            int newPage = Math.min(totalPages, page + 1);
            renderPlaylistDetailsView(event, id.playlistIndex(), id.listPage(), newPage, 0, id.userId(), playlistName, draftContext);
            return;
        }

        MusicService musicService = bot.getMusicService();
        if (action.equals("queueall") || action.equals("playall"))
        {
            if (!InteractionGuards.ensureBotInUserVoiceChannel(event, bot))
            {
                return;
            }

            GuildMessageChannel channel = event.getChannel().asGuildMessageChannel();
            MusicService.OutputAdapter output = OutputAdapters.forPlaylistsReply(event);
            if (action.equals("queueall"))
            {
                musicService.queuePlaylist(event.getGuild(), event.getMember(), playlistName, channel, output);
                renderPlaylistDetailsView(event, id.playlistIndex(), id.listPage(), page, 0, id.userId(), playlistName, draftContext);
            }
            else
            {
                musicService.playPlaylistNow(event.getGuild(), event.getMember(), playlistName, channel, output);
                renderPlaylistDetailsView(event, id.playlistIndex(), id.listPage(), 1, 0, id.userId(), playlistName, draftContext);
            }
            return;
        }

        if (action.equals("save") || action.equals("discard"))
        {
            if (!canEditPlaylist)
            {
                event.reply("You need to be a DJ or the bot owner to edit playlists!").setEphemeral(true).queue();
                return;
            }
            if (action.equals("save"))
            {
                MusicService.PlaylistDraftMutationResult saveResult = musicService.savePlaylistDraft(draftContext);
                if (!saveResult.success)
                {
                    event.reply(saveResult.errorMessage).setEphemeral(true).queue();
                    return;
                }
            }
            else
            {
                musicService.discardPlaylistDraft(draftContext);
            }
            renderPlaylistDetailsView(event, id.playlistIndex(), id.listPage(), page, 0, id.userId(), playlistName, draftContext);
            return;
        }

        if (action.equals("unselect"))
        {
            renderPlaylistDetailsView(event, id.playlistIndex(), id.listPage(), page, 0, id.userId(), playlistName, draftContext);
            return;
        }

        if (selectedTrack <= 0 || selectedTrack > totalItems)
        {
            event.reply("No track selected!").setEphemeral(true).queue();
            return;
        }

        if (action.equals("remove"))
        {
            if (!canEditPlaylist)
            {
                event.reply("You need to be a DJ or the bot owner to edit playlists!").setEphemeral(true).queue();
                return;
            }
            MusicService.PlaylistDraftMutationResult removeResult = musicService.removePlaylistDraftItem(draftContext, selectedTrack);
            if (!removeResult.success)
            {
                event.reply(removeResult.errorMessage).setEphemeral(true).queue();
                return;
            }
            int newTotalPages = Math.max(1, PlaylistsSlashCmd.getPlaylistTrackTotalPages(removeResult.totalItems));
            int safePage = Math.min(page, newTotalPages);
            int newSelectedTrack = removeResult.totalItems == 0 ? 0 : Math.min(selectedTrack, removeResult.totalItems);
            renderPlaylistDetailsView(event, id.playlistIndex(), id.listPage(), safePage, newSelectedTrack, id.userId(), playlistName, draftContext);
            return;
        }

        if (action.equals("move"))
        {
            if (!canEditPlaylist)
            {
                event.reply("You need to be a DJ or the bot owner to edit playlists!").setEphemeral(true).queue();
                return;
            }
            renderPlaylistMoveMenu(event, draftContext, id.playlistIndex(), id.listPage(), page, selectedTrack, id.userId());
            return;
        }
        if (action.equals("cancelmove"))
        {
            renderPlaylistDetailsView(event, id.playlistIndex(), id.listPage(), page, selectedTrack, id.userId(), playlistName, draftContext);
            return;
        }

        if (action.equals("movetop") || action.equals("moveup") || action.equals("movedown") || action.equals("movebottom"))
        {
            if (!canEditPlaylist)
            {
                event.reply("You need to be a DJ or the bot owner to edit playlists!").setEphemeral(true).queue();
                return;
            }
            int target = switch (action)
            {
                case "movetop" -> 1;
                case "moveup" -> Math.max(1, selectedTrack - 1);
                case "movedown" -> Math.min(totalItems, selectedTrack + 1);
                case "movebottom" -> totalItems;
                default -> selectedTrack;
            };
            MusicService.PlaylistDraftMutationResult moveResult =
                    musicService.movePlaylistDraftItem(draftContext, selectedTrack, target);
            if (!moveResult.success)
            {
                event.reply(moveResult.errorMessage).setEphemeral(true).queue();
                return;
            }
            renderPlaylistDetailsView(event, id.playlistIndex(), id.listPage(), page, target, id.userId(), playlistName, draftContext);
            return;
        }

        if (!InteractionGuards.ensureBotInUserVoiceChannel(event, bot))
        {
            return;
        }

        GuildMessageChannel channel = event.getChannel().asGuildMessageChannel();
        MusicService.OutputAdapter output = OutputAdapters.forPlaylistsReply(event);
        String selectedTrackUrl = musicService.getPlaylistTrackUrlAtPosition(draftContext, selectedTrack);
        if (selectedTrackUrl == null || selectedTrackUrl.isBlank())
        {
            event.reply("That playlist entry could not be loaded.").setEphemeral(true).queue();
            return;
        }

        if (action.equals("queue"))
        {
            musicService.play(event.getGuild(), event.getMember(), selectedTrackUrl, channel, output);
            renderPlaylistDetailsView(event, id.playlistIndex(), id.listPage(), page, selectedTrack, id.userId(), playlistName, draftContext);
            return;
        }
        if (action.equals("playnext"))
        {
            musicService.playNext(event.getGuild(), event.getMember(), selectedTrackUrl, channel, output);
            renderPlaylistDetailsView(event, id.playlistIndex(), id.listPage(), page, selectedTrack, id.userId(), playlistName, draftContext);
            return;
        }
        if (action.equals("playnow"))
        {
            musicService.playNowFromUrl(event.getGuild(), event.getMember(), selectedTrackUrl, channel, output);
            renderPlaylistDetailsView(event, id.playlistIndex(), id.listPage(), page, selectedTrack, id.userId(), playlistName, draftContext);
        }
    }

    private void handlePlaylistDetailsMoveSelect(StringSelectInteractionEvent event)
    {
        if (!InteractionGuards.requireGuildAndMember(event))
        {
            return;
        }

        Optional<ComponentIdParsers.PlaylistDetailsMoveSelectId> parsed =
                ComponentIdParsers.parsePlaylistDetailsMoveSelectId(event.getComponentId());
        if (parsed.isEmpty())
        {
            event.reply("Invalid selection state.").setEphemeral(true).queue();
            return;
        }
        ComponentIdParsers.PlaylistDetailsMoveSelectId id = parsed.get();
        if (event.getUser().getIdLong() != id.userId())
        {
            event.reply("Only the user who ran the command can use this!").setEphemeral(true).queue();
            return;
        }

        MusicService musicService = bot.getMusicService();
        if (!musicService.canEditPlaylistEntries(event.getGuild(), event.getMember()))
        {
            event.reply("You need to be a DJ or the bot owner to edit playlists!").setEphemeral(true).queue();
            return;
        }

        MusicService.PlaylistNamesInfo namesInfo = loadPlaylistNames();
        if (namesInfo.hasError())
        {
            event.reply(namesInfo.errorMessage).setEphemeral(true).queue();
            return;
        }
        if (id.playlistIndex() <= 0 || id.playlistIndex() > namesInfo.names.size())
        {
            event.reply("Playlist no longer exists. Click Back and Refresh.").setEphemeral(true).queue();
            return;
        }
        String playlistName = namesInfo.names.get(id.playlistIndex() - 1);

        int toPosition;
        try
        {
            toPosition = Integer.parseInt(event.getValues().get(0));
        }
        catch (NumberFormatException e)
        {
            event.reply("Invalid position selected.").setEphemeral(true).queue();
            return;
        }

        MusicService.PlaylistDraftContext draftContext = buildDraftContext(event, id.userId(), playlistName);
        MusicService.PlaylistDraftMutationResult moveResult =
                musicService.movePlaylistDraftItem(draftContext, id.fromTrack(), toPosition);
        if (!moveResult.success)
        {
            event.reply(moveResult.errorMessage).setEphemeral(true).queue();
            return;
        }

        renderPlaylistDetailsView(event, id.playlistIndex(), id.listPage(), id.detailsPage(), toPosition, id.userId(), playlistName, draftContext);
    }

    private void handlePlaylistsButton(ButtonInteractionEvent event)
    {
        if (!InteractionGuards.requireGuildAndMember(event))
        {
            return;
        }

        Optional<ComponentIdParsers.PaginatedButtonId> parsed = ComponentIdParsers.parsePlaylistsButtonId(event.getComponentId());
        if (parsed.isEmpty())
        {
            event.reply("Invalid button state.").setEphemeral(true).queue();
            return;
        }
        ComponentIdParsers.PaginatedButtonId id = parsed.get();
        if (event.getUser().getIdLong() != id.userId())
        {
            event.reply("Only the user who ran the command can use these buttons!").setEphemeral(true).queue();
            return;
        }

        MusicService.PlaylistNamesInfo playlistNamesInfo = loadPlaylistNames();
        if (playlistNamesInfo.hasError())
        {
            event.reply(playlistNamesInfo.errorMessage).setEphemeral(true).queue();
            return;
        }
        List<String> playlists = playlistNamesInfo.names;
        if (playlists.isEmpty())
        {
            event.editMessage("There are no playlists in the Playlists folder!").setEmbeds().setComponents().queue();
            return;
        }

        int requestedPage = id.page();
        int selectedIndex = id.selectedTrack();
        long userId = id.userId();
        String action = id.action();

        int totalPages = PlaylistsSlashCmd.getTotalPages(playlists.size());
        int page = Math.max(1, Math.min(requestedPage, totalPages));

        if (action.startsWith("select"))
        {
            int newSelectedIndex;
            try
            {
                newSelectedIndex = Integer.parseInt(action.substring(6));
            }
            catch (NumberFormatException e)
            {
                event.reply("Invalid playlist selection.").setEphemeral(true).queue();
                return;
            }
            int playlistsOnPage = PlaylistsSlashCmd.getPlaylistsOnPage(page, playlists.size());
            int firstOnPage = (page - 1) * PlaylistsSlashCmd.PLAYLISTS_PER_PAGE + 1;
            int lastOnPage = firstOnPage + playlistsOnPage - 1;
            if (newSelectedIndex < firstOnPage || newSelectedIndex > lastOnPage)
            {
                event.reply("That playlist isn't on this page!").setEphemeral(true).queue();
                return;
            }
            if (newSelectedIndex == selectedIndex)
            {
                newSelectedIndex = 0;
            }
            if (newSelectedIndex > playlists.size())
            {
                event.reply("That playlist doesn't exist!").setEphemeral(true).queue();
                return;
            }
            updatePlaylistsEmbed(event, playlists, page, totalPages, newSelectedIndex, userId);
            return;
        }

        if (action.equals("prev"))
        {
            int newPage = Math.max(1, page - 1);
            updatePlaylistsEmbed(event, playlists, newPage, totalPages, 0, userId);
            return;
        }
        if (action.equals("next"))
        {
            int newPage = Math.min(totalPages, page + 1);
            updatePlaylistsEmbed(event, playlists, newPage, totalPages, 0, userId);
            return;
        }
        if (action.equals("refresh"))
        {
            int safePage = Math.min(page, totalPages);
            updatePlaylistsEmbed(event, playlists, safePage, totalPages, 0, userId);
            return;
        }

        if (selectedIndex <= 0 || selectedIndex > playlists.size())
        {
            event.reply("No playlist selected!").setEphemeral(true).queue();
            return;
        }

        String selectedPlaylist = playlists.get(selectedIndex - 1);
        MusicService musicService = bot.getMusicService();
        if (action.equals("details"))
        {
            if (musicService.getPlaylistDetails(selectedPlaylist) == null)
            {
                event.reply("Playlist no longer exists. Click Refresh.").setEphemeral(true).queue();
                return;
            }
            MusicService.PlaylistDraftContext draftContext = buildDraftContext(event, userId, selectedPlaylist);
            event.deferEdit().queue(hook -> renderPlaylistDetailsView(
                    hook,
                    event,
                    selectedIndex,
                    page,
                    1,
                    0,
                    userId,
                    selectedPlaylist,
                    draftContext
            ));
            return;
        }

        if (!InteractionGuards.ensureBotInUserVoiceChannel(event, bot))
        {
            return;
        }

        GuildMessageChannel channel = event.getChannel().asGuildMessageChannel();
        MusicService.OutputAdapter output = OutputAdapters.forPlaylistsReply(event);
        if (action.equals("queue"))
        {
            musicService.queuePlaylist(event.getGuild(), event.getMember(), selectedPlaylist, channel, output);
            updatePlaylistsEmbed(event, playlists, page, totalPages, 0, userId);
            return;
        }
        if (action.equals("playnow"))
        {
            musicService.playPlaylistNow(event.getGuild(), event.getMember(), selectedPlaylist, channel, output);
            updatePlaylistsEmbed(event, playlists, page, totalPages, 0, userId);
        }
    }

    private MusicService.PlaylistNamesInfo loadPlaylistNames()
    {
        return bot.getMusicService().getAvailablePlaylistNames();
    }

    private void updatePlaylistsEmbed(ButtonInteractionEvent event, List<String> playlists,
                                      int page, int totalPages, int selectedIndex, long userId)
    {
        int playlistsOnPage = PlaylistsSlashCmd.getPlaylistsOnPage(page, playlists.size());
        MessageEmbed embed = PlaylistsSlashCmd.buildPlaylistsEmbed(playlists, page, totalPages, selectedIndex,
                event.getMember().getColor());
        List<ActionRow> components = PlaylistsSlashCmd.buildPlaylistsComponents(page, totalPages, playlistsOnPage, selectedIndex, userId);
        event.editMessageEmbeds(embed).setComponents(components).queue();
    }

    private void renderPlaylistListView(ButtonInteractionEvent event, int listPage, int listSelectedIndex, long userId)
    {
        MusicService.PlaylistNamesInfo playlistNamesInfo = loadPlaylistNames();
        if (playlistNamesInfo.hasError())
        {
            event.reply(playlistNamesInfo.errorMessage).setEphemeral(true).queue();
            return;
        }
        List<String> playlists = playlistNamesInfo.names;
        if (playlists.isEmpty())
        {
            event.editMessage("There are no playlists in the Playlists folder!").setEmbeds().setComponents().queue();
            return;
        }
        int totalPages = PlaylistsSlashCmd.getTotalPages(playlists.size());
        int safePage = Math.max(1, Math.min(listPage, totalPages));
        int safeSelected = listSelectedIndex > playlists.size() ? 0 : Math.max(0, listSelectedIndex);

        int playlistsOnPage = PlaylistsSlashCmd.getPlaylistsOnPage(safePage, playlists.size());
        int firstOnPage = (safePage - 1) * PlaylistsSlashCmd.PLAYLISTS_PER_PAGE + 1;
        int lastOnPage = firstOnPage + playlistsOnPage - 1;
        if (safeSelected < firstOnPage || safeSelected > lastOnPage)
        {
            safeSelected = 0;
        }
        updatePlaylistsEmbed(event, playlists, safePage, totalPages, safeSelected, userId);
    }

    private void renderPlaylistDetailsView(ButtonInteractionEvent event, int playlistIndex, int listPage,
                                           int detailsPage, int selectedTrack, long userId, String playlistName,
                                           MusicService.PlaylistDraftContext draftContext)
    {
        renderPlaylistDetailsView(result ->
                        event.editMessageEmbeds(result.embed()).setComponents(result.components()).queue(),
                message -> event.editMessage(message).setEmbeds().setComponents().queue(),
                event.getGuild(), event.getMember(), playlistIndex, listPage, detailsPage, selectedTrack, userId,
                playlistName, draftContext);
    }

    private void renderPlaylistDetailsView(StringSelectInteractionEvent event, int playlistIndex, int listPage,
                                           int detailsPage, int selectedTrack, long userId, String playlistName,
                                           MusicService.PlaylistDraftContext draftContext)
    {
        renderPlaylistDetailsView(result ->
                        event.editMessageEmbeds(result.embed()).setComponents(result.components()).queue(),
                message -> event.editMessage(message).setEmbeds().setComponents().queue(),
                event.getGuild(), event.getMember(), playlistIndex, listPage, detailsPage, selectedTrack, userId,
                playlistName, draftContext);
    }

    private void renderPlaylistDetailsView(net.dv8tion.jda.api.interactions.InteractionHook hook,
                                           ButtonInteractionEvent event,
                                           int playlistIndex, int listPage, int detailsPage,
                                           int selectedTrack, long userId, String playlistName,
                                           MusicService.PlaylistDraftContext draftContext)
    {
        renderPlaylistDetailsView(result ->
                        hook.editOriginalEmbeds(result.embed()).setComponents(result.components()).queue(),
                message -> hook.editOriginal(message).setEmbeds(java.util.Collections.emptyList()).setComponents(java.util.Collections.emptyList()).queue(),
                event.getGuild(), event.getMember(), playlistIndex, listPage, detailsPage, selectedTrack, userId,
                playlistName, draftContext);
    }

    private void renderPlaylistDetailsView(java.util.function.Consumer<PlaylistDetailsRenderResult> renderTarget,
                                           Consumer<String> onMissingPlaylist,
                                           net.dv8tion.jda.api.entities.Guild guild,
                                           net.dv8tion.jda.api.entities.Member member,
                                           int playlistIndex, int listPage, int detailsPage,
                                           int selectedTrack, long userId, String playlistName,
                                           MusicService.PlaylistDraftContext draftContext)
    {
        MusicService musicService = bot.getMusicService();
        musicService.loadPlaylistPageWithTracks(
                draftContext, detailsPage, PlaylistsSlashCmd.PLAYLIST_TRACKS_PER_PAGE, result ->
                {
                    if (result == null)
                    {
                        onMissingPlaylist.accept("Playlist no longer exists. Click Back and Refresh.");
                        return;
                    }
                    int totalPages = Math.max(1, PlaylistsSlashCmd.getPlaylistTrackTotalPages(result.totalItems));
                    int safePage = Math.max(1, Math.min(detailsPage, totalPages));
                    int tracksOnPage = PlaylistsSlashCmd.getPlaylistTracksOnPage(safePage, result.totalItems);
                    int firstOnPage = (safePage - 1) * PlaylistsSlashCmd.PLAYLIST_TRACKS_PER_PAGE + 1;
                    int lastOnPage = firstOnPage + tracksOnPage - 1;
                    int safeSelected = (selectedTrack >= firstOnPage && selectedTrack <= lastOnPage) ? selectedTrack : 0;

                    java.awt.Color color = member != null
                            ? member.getColor()
                            : null;
                    MessageEmbed embed = PlaylistsSlashCmd.buildPlaylistTracksEmbed(
                            result.playlistName, result.totalItems, safePage, totalPages, result.formattedLines,
                            safeSelected, result.dirty, color);
                    boolean canEdit = guild != null
                            && member != null
                            && musicService.canEditPlaylistEntries(guild, member);
                    List<ActionRow> components = PlaylistsSlashCmd.buildPlaylistDetailsComponents(
                            playlistIndex, listPage, safePage, totalPages, tracksOnPage, safeSelected, userId,
                            canEdit, result.dirty);
                    renderTarget.accept(new PlaylistDetailsRenderResult(embed, components));
                });
    }

    private void renderPlaylistMoveMenu(ButtonInteractionEvent event, MusicService.PlaylistDraftContext draftContext,
                                        int playlistIndex, int listPage, int detailsPage, int selectedTrack,
                                        long userId)
    {
        bot.getMusicService().loadPlaylistPageWithTracks(
                draftContext, detailsPage, PlaylistsSlashCmd.PLAYLIST_TRACKS_PER_PAGE, result ->
                {
                    if (result == null)
                    {
                        event.reply("Playlist no longer exists. Click Back and Refresh.").setEphemeral(true).queue();
                        return;
                    }
                    int totalPages = Math.max(1, PlaylistsSlashCmd.getPlaylistTrackTotalPages(result.totalItems));
                    int safePage = Math.max(1, Math.min(detailsPage, totalPages));
                    int firstOnPage = (safePage - 1) * PlaylistsSlashCmd.PLAYLIST_TRACKS_PER_PAGE + 1;
                    int tracksOnPage = PlaylistsSlashCmd.getPlaylistTracksOnPage(safePage, result.totalItems);
                    int lastOnPage = firstOnPage + tracksOnPage - 1;
                    int safeSelected = (selectedTrack >= firstOnPage && selectedTrack <= lastOnPage) ? selectedTrack : 0;

                    MessageEmbed embed = PlaylistsSlashCmd.buildPlaylistTracksEmbed(
                            result.playlistName,
                            result.totalItems,
                            safePage,
                            totalPages,
                            result.formattedLines,
                            safeSelected,
                            result.dirty,
                            event.getMember() != null ? event.getMember().getColor() : null
                    );

                    StringSelectMenu.Builder menuBuilder = StringSelectMenu
                            .create("playlistdetails_move_select_" + playlistIndex + "_" + listPage + "_"
                                    + safeSelected + "_" + safePage + "_" + userId)
                            .setPlaceholder("Select new position")
                            .setMinValues(1)
                            .setMaxValues(1);
                    int maxOptions = Math.min(result.totalItems, 25);
                    for (int i = 1; i <= maxOptions; i++)
                    {
                        if (i != safeSelected)
                        {
                            menuBuilder.addOption("Position " + i, String.valueOf(i));
                        }
                    }
                    String cancelMoveId = "playlistdetails_" + playlistIndex + "_" + listPage + "_cancelmove_"
                            + safePage + "_" + safeSelected + "_" + userId;
                    event.editMessageEmbeds(embed)
                            .setComponents(
                                    ActionRow.of(menuBuilder.build()),
                                    ActionRow.of(net.dv8tion.jda.api.components.buttons.Button.secondary(cancelMoveId, "Back"))
                            )
                            .queue();
                });
    }

    private MusicService.PlaylistDraftContext buildDraftContext(ButtonInteractionEvent event, long userId, String playlistName)
    {
        return bot.getMusicService().buildPlaylistDraftContext(
                event.getGuild().getIdLong(),
                event.getChannel().getIdLong(),
                event.getMessageIdLong(),
                userId,
                playlistName
        );
    }

    private MusicService.PlaylistDraftContext buildDraftContext(StringSelectInteractionEvent event, long userId, String playlistName)
    {
        return bot.getMusicService().buildPlaylistDraftContext(
                event.getGuild().getIdLong(),
                event.getChannel().getIdLong(),
                event.getMessageIdLong(),
                userId,
                playlistName
        );
    }

    private record PlaylistDetailsRenderResult(MessageEmbed embed, List<ActionRow> components)
    {
    }
}
