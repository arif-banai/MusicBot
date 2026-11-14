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
package com.jagrosh.jmusicbot.unit;

import com.jagrosh.jmusicbot.listener.PlaylistsInteractionListener;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.testutil.listener.InteractionAckTracker;
import com.jagrosh.jmusicbot.testutil.listener.ListenerTestFixture;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.unions.GuildMessageChannelUnion;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("PlaylistsInteractionListener Tests")
class PlaylistsInteractionListenerTest
{
    private ListenerTestFixture fixture;
    private PlaylistsInteractionListener listener;

    @BeforeEach
    void setUp()
    {
        fixture = ListenerTestFixture.create();
        listener = new PlaylistsInteractionListener(fixture.getBot());
        when(fixture.getButtonInteractionEvent().getUser()).thenReturn(fixture.getUser());
    }

    @Test
    @DisplayName("onButtonInteraction() handles playlists_ button with invalid format and replies error")
    void onButtonInteraction_playlistsInvalidFormat_repliesError()
    {
        fixture.withButtonId("playlists_ab");

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        verify(fixture.getButtonInteractionEvent()).reply(argThat((String s) -> s.contains("Invalid button state")));
        verify(fixture.getReplyAction()).setEphemeral(true);
        verify(fixture.getMusicService(), never()).queuePlaylist(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("onButtonInteraction() blocks users other than command invoker")
    void onButtonInteraction_wrongUser_repliesError()
    {
        fixture.withButtonId("playlists_prev_1_0_999");

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        verify(fixture.getButtonInteractionEvent()).reply(argThat((String s) -> s.contains("Only the user who ran the command")));
        verify(fixture.getReplyAction()).setEphemeral(true);
        verify(fixture.getMusicService(), never()).queuePlaylist(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("onButtonInteraction() queue action dispatches to MusicService")
    void onButtonInteraction_queue_dispatchesToMusicService()
    {
        fixture.withButtonId("playlists_queue_1_1_" + fixture.getUser().getIdLong())
                .withMemberInVoiceChannel();

        when(fixture.getMusicService().getAvailablePlaylistNames())
                .thenReturn(MusicService.PlaylistNamesInfo.success(List.of("favorite")));

        MessageChannelUnion channelUnion = mock(MessageChannelUnion.class);
        when(channelUnion.asGuildMessageChannel())
                .thenReturn((GuildMessageChannelUnion) fixture.getTextChannel());
        when(fixture.getButtonInteractionEvent().getChannel()).thenReturn(channelUnion);

        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        assertEquals(List.of("deferEdit"), acks.acks());

        verify(fixture.getMusicService()).queuePlaylist(
                eq(fixture.getGuild()),
                eq(fixture.getMember()),
                eq("favorite"),
                eq(fixture.getTextChannel()),
                any(MusicService.OutputAdapter.class)
        );
    }

    @Test
    @DisplayName("onButtonInteraction() rejects select action not present on current page")
    void onButtonInteraction_selectOutsidePage_repliesError()
    {
        fixture.withButtonId("playlists_select11_1_0_" + fixture.getUser().getIdLong());
        when(fixture.getMusicService().getAvailablePlaylistNames()).thenReturn(MusicService.PlaylistNamesInfo.success(List.of(
                "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"
        )));

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        verify(fixture.getButtonInteractionEvent()).reply(argThat((String s) -> s.contains("isn't on this page")));
        verify(fixture.getReplyAction()).setEphemeral(true);
    }

    @Test
    @DisplayName("onButtonInteraction() playlistdetails queueall dispatches full playlist queue")
    void onButtonInteraction_playlistDetailsQueueAll_dispatchesToMusicService()
    {
        fixture.withButtonId("playlistdetails_1_1_queueall_1_0_" + fixture.getUser().getIdLong())
                .withMemberInVoiceChannel();
        when(fixture.getMusicService().getAvailablePlaylistNames())
                .thenReturn(MusicService.PlaylistNamesInfo.success(List.of("favorite")));
        when(fixture.getMusicService().getPlaylistDetails("favorite"))
                .thenReturn(new MusicService.PlaylistDetailsInfo("favorite", 3, List.of(), false));
        when(fixture.getMusicService().getPlaylistTrackCount(any())).thenReturn(3);

        MessageChannelUnion channelUnion = mock(MessageChannelUnion.class);
        when(channelUnion.asGuildMessageChannel())
                .thenReturn((GuildMessageChannelUnion) fixture.getTextChannel());
        when(channelUnion.getIdLong()).thenReturn(123L);
        when(fixture.getButtonInteractionEvent().getChannel()).thenReturn(channelUnion);
        when(fixture.getButtonInteractionEvent().getMessageIdLong()).thenReturn(456L);

        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        assertEquals(List.of("deferEdit"), acks.acks());

        verify(fixture.getMusicService()).queuePlaylist(
                eq(fixture.getGuild()),
                eq(fixture.getMember()),
                eq("favorite"),
                eq(fixture.getTextChannel()),
                any(MusicService.OutputAdapter.class)
        );
    }

    @Test
    @DisplayName("onButtonInteraction() playlistdetails remove requires DJ or owner")
    void onButtonInteraction_playlistDetailsRemove_requiresDjOrOwner()
    {
        fixture.withButtonId("playlistdetails_1_1_remove_1_1_" + fixture.getUser().getIdLong());
        when(fixture.getMusicService().getAvailablePlaylistNames())
                .thenReturn(MusicService.PlaylistNamesInfo.success(List.of("favorite")));
        when(fixture.getMusicService().getPlaylistDetails("favorite"))
                .thenReturn(new MusicService.PlaylistDetailsInfo("favorite", 3, List.of(), false));
        when(fixture.getMusicService().getPlaylistTrackCount(any())).thenReturn(3);
        when(fixture.getMusicService().canEditPlaylistEntries(eq(fixture.getGuild()), eq(fixture.getMember())))
                .thenReturn(false);
        when(fixture.getButtonInteractionEvent().getMessageIdLong()).thenReturn(456L);

        MessageChannelUnion channelUnion = mock(MessageChannelUnion.class);
        when(channelUnion.getIdLong()).thenReturn(123L);
        when(fixture.getButtonInteractionEvent().getChannel()).thenReturn(channelUnion);

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        verify(fixture.getButtonInteractionEvent()).reply(argThat((String s) -> s.contains("DJ or the bot owner")));
        verify(fixture.getReplyAction()).setEphemeral(true);
    }

    @Test
    @DisplayName("onButtonInteraction() playlistdetails unselect re-renders details unselected state")
    void onButtonInteraction_playlistDetailsUnselect_rerendersDetails()
    {
        fixture.withButtonId("playlistdetails_1_1_unselect_1_4_" + fixture.getUser().getIdLong());
        when(fixture.getMusicService().getAvailablePlaylistNames())
                .thenReturn(MusicService.PlaylistNamesInfo.success(List.of("favorite")));
        when(fixture.getMusicService().getPlaylistDetails("favorite"))
                .thenReturn(new MusicService.PlaylistDetailsInfo("favorite", 4, List.of(), false));
        when(fixture.getMusicService().getPlaylistTrackCount(any())).thenReturn(4);
        when(fixture.getMusicService().canEditPlaylistEntries(eq(fixture.getGuild()), eq(fixture.getMember())))
                .thenReturn(true);
        MusicService.PlaylistDraftContext draftContext =
                new MusicService.PlaylistDraftContext(123456789L, 123L, 456L, fixture.getUser().getIdLong(), "favorite");
        when(fixture.getMusicService().buildPlaylistDraftContext(anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(draftContext);
        doAnswer(invocation ->
        {
            java.util.function.Consumer<MusicService.PlaylistTracksPageInfo> callback =
                    invocation.getArgument(3);
            callback.accept(new MusicService.PlaylistTracksPageInfo(
                    "favorite", 4, 0, false, false, List.of(
                    "`[03:57]` [**spinnin**](https://example/1)",
                    "`[05:57]` [**daylight**](https://example/2)",
                    "`[04:31]` [**rapture**](https://example/3)",
                    "`[06:40]` [**flame**](https://example/4)"
            )));
            return null;
        }).when(fixture.getMusicService()).loadPlaylistPageWithTracks(any(MusicService.PlaylistDraftContext.class), anyInt(), anyInt(), any());

        MessageChannelUnion channelUnion = mock(MessageChannelUnion.class);
        when(channelUnion.getIdLong()).thenReturn(123L);
        when(fixture.getButtonInteractionEvent().getChannel()).thenReturn(channelUnion);
        when(fixture.getButtonInteractionEvent().getMessageIdLong()).thenReturn(456L);

        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        assertEquals(List.of("deferEdit"), acks.acks());

        verify(fixture.getButtonInteractionEvent(), never()).reply(argThat((String s) -> s.contains("No track selected")));
        verify(fixture.getMusicService()).loadPlaylistPageWithTracks(any(MusicService.PlaylistDraftContext.class), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("onButtonInteraction() playlistdetails cancelmove returns to selected details")
    void onButtonInteraction_playlistDetailsCancelMove_rerendersSelectedDetails()
    {
        fixture.withButtonId("playlistdetails_1_1_cancelmove_1_2_" + fixture.getUser().getIdLong());
        when(fixture.getMusicService().getAvailablePlaylistNames())
                .thenReturn(MusicService.PlaylistNamesInfo.success(List.of("favorite")));
        when(fixture.getMusicService().getPlaylistDetails("favorite"))
                .thenReturn(new MusicService.PlaylistDetailsInfo("favorite", 4, List.of(), false));
        when(fixture.getMusicService().getPlaylistTrackCount(any())).thenReturn(4);
        when(fixture.getMusicService().canEditPlaylistEntries(eq(fixture.getGuild()), eq(fixture.getMember())))
                .thenReturn(true);
        MusicService.PlaylistDraftContext draftContext =
                new MusicService.PlaylistDraftContext(123456789L, 123L, 456L, fixture.getUser().getIdLong(), "favorite");
        when(fixture.getMusicService().buildPlaylistDraftContext(anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(draftContext);
        doAnswer(invocation ->
        {
            java.util.function.Consumer<MusicService.PlaylistTracksPageInfo> callback =
                    invocation.getArgument(3);
            callback.accept(new MusicService.PlaylistTracksPageInfo(
                    "favorite", 4, 0, false, false, List.of(
                    "`[03:57]` [**spinnin**](https://example/1)",
                    "`[05:57]` [**daylight**](https://example/2)",
                    "`[04:31]` [**rapture**](https://example/3)",
                    "`[06:40]` [**flame**](https://example/4)"
            )));
            return null;
        }).when(fixture.getMusicService()).loadPlaylistPageWithTracks(any(MusicService.PlaylistDraftContext.class), anyInt(), anyInt(), any());

        MessageChannelUnion channelUnion = mock(MessageChannelUnion.class);
        when(channelUnion.getIdLong()).thenReturn(123L);
        when(fixture.getButtonInteractionEvent().getChannel()).thenReturn(channelUnion);
        when(fixture.getButtonInteractionEvent().getMessageIdLong()).thenReturn(456L);

        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        assertEquals(List.of("deferEdit"), acks.acks());

        verify(fixture.getMusicService()).loadPlaylistPageWithTracks(any(MusicService.PlaylistDraftContext.class), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("onButtonInteraction() playlistdetails back discards dirty draft before list view")
    void onButtonInteraction_playlistDetailsBack_discardsDirtyDraft()
    {
        fixture.withButtonId("playlistdetails_back_1_1_" + fixture.getUser().getIdLong());
        when(fixture.getMusicService().getAvailablePlaylistNames())
                .thenReturn(MusicService.PlaylistNamesInfo.success(List.of("favorite")));
        MusicService.PlaylistDraftContext draftContext =
                new MusicService.PlaylistDraftContext(123456789L, 123L, 456L, fixture.getUser().getIdLong(), "favorite");
        when(fixture.getMusicService().buildPlaylistDraftContext(anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(draftContext);
        when(fixture.getMusicService().isPlaylistDraftDirty(draftContext)).thenReturn(true);

        MessageChannelUnion channelUnion = mock(MessageChannelUnion.class);
        when(channelUnion.getIdLong()).thenReturn(123L);
        when(fixture.getButtonInteractionEvent().getChannel()).thenReturn(channelUnion);
        when(fixture.getButtonInteractionEvent().getMessageIdLong()).thenReturn(456L);

        MessageEditCallbackAction editAction = mock(MessageEditCallbackAction.class);
        when(fixture.getButtonInteractionEvent().editMessageEmbeds(any(MessageEmbed[].class))).thenReturn(editAction);
        when(editAction.setComponents(anyList())).thenReturn(editAction);
        doNothing().when(editAction).queue();

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        verify(fixture.getMusicService()).discardPlaylistDraft(draftContext);
        verify(fixture.getButtonInteractionEvent()).editMessageEmbeds(any(MessageEmbed[].class));
    }

    // ==================== Acknowledgement: exactly once, later output via the hook ====================

    private static final MusicService.PlaylistTracksPageInfo FAVORITE_PAGE = new MusicService.PlaylistTracksPageInfo(
            "favorite", 3, 0, false, false, List.of(
            "`[03:57]` [**spinnin**](https://example/1)",
            "`[05:57]` [**daylight**](https://example/2)",
            "`[04:31]` [**rapture**](https://example/3)"));

    /**
     * Stubs a 3-track "favorite" playlist for the details view and captures the page-load callback
     * without running it, so the test decides when the (network-bound) re-render finishes.
     */
    private AtomicReference<Consumer<MusicService.PlaylistTracksPageInfo>> stubFavoriteDetails()
    {
        when(fixture.getMusicService().getAvailablePlaylistNames())
                .thenReturn(MusicService.PlaylistNamesInfo.success(List.of("favorite")));
        when(fixture.getMusicService().getPlaylistDetails("favorite"))
                .thenReturn(new MusicService.PlaylistDetailsInfo("favorite", 3, List.of(), false));
        when(fixture.getMusicService().getPlaylistTrackCount(any())).thenReturn(3);
        MusicService.PlaylistDraftContext draftContext =
                new MusicService.PlaylistDraftContext(123456789L, 123L, 456L, fixture.getUser().getIdLong(), "favorite");
        when(fixture.getMusicService().buildPlaylistDraftContext(anyLong(), anyLong(), anyLong(), anyLong(), anyString()))
                .thenReturn(draftContext);
        stubChannel();
        when(fixture.getButtonInteractionEvent().getMessageIdLong()).thenReturn(456L);

        AtomicReference<Consumer<MusicService.PlaylistTracksPageInfo>> render = new AtomicReference<>();
        doAnswer(invocation ->
        {
            render.set(invocation.getArgument(3));
            return null;
        }).when(fixture.getMusicService()).loadPlaylistPageWithTracks(any(MusicService.PlaylistDraftContext.class), anyInt(), anyInt(), any());
        return render;
    }

    private void stubChannel()
    {
        MessageChannelUnion channelUnion = mock(MessageChannelUnion.class);
        when(channelUnion.asGuildMessageChannel())
                .thenReturn((GuildMessageChannelUnion) fixture.getTextChannel());
        when(channelUnion.getIdLong()).thenReturn(123L);
        when(fixture.getButtonInteractionEvent().getChannel()).thenReturn(channelUnion);
    }

    private void assertEphemeralFollowup(InteractionAckTracker acks, String content)
    {
        verify(acks.hook()).sendMessage(content);
        verify(acks.followup()).setEphemeral(true);
        verify(acks.followup()).queue();
        verify(fixture.getButtonInteractionEvent(), never()).reply(anyString());
    }

    @Test
    @DisplayName("playlistdetails playall: re-render first, then the playlist-loaded reply goes through the hook (E5)")
    void playlistDetailsPlayAll_renderThenLoaded_acknowledgesOnce()
    {
        fixture.withButtonId("playlistdetails_1_1_playall_1_0_" + fixture.getUser().getIdLong())
                .withMemberInVoiceChannel();
        AtomicReference<Consumer<MusicService.PlaylistTracksPageInfo>> render = stubFavoriteDetails();
        AtomicReference<MusicService.OutputAdapter> output = new AtomicReference<>();
        doAnswer(invocation ->
        {
            output.set(invocation.getArgument(4));
            return null;
        }).when(fixture.getMusicService()).playPlaylistNow(any(), any(), eq("favorite"), any(), any());
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());
        render.get().accept(FAVORITE_PAGE);
        output.get().replySuccess("Now playing playlist `favorite` (queue replaced).");

        assertEquals(List.of("deferEdit"), acks.acks());
        verify(acks.hook()).editOriginalEmbeds(any(MessageEmbed[].class));
        assertEphemeralFollowup(acks, "Now playing playlist `favorite` (queue replaced).");
    }

    @Test
    @DisplayName("playlistdetails queueall: an immediate error reply still leaves the view re-render one acknowledgement")
    void playlistDetailsQueueAll_immediateError_acknowledgesOnce()
    {
        fixture.withButtonId("playlistdetails_1_1_queueall_1_0_" + fixture.getUser().getIdLong())
                .withMemberInVoiceChannel();
        AtomicReference<Consumer<MusicService.PlaylistTracksPageInfo>> render = stubFavoriteDetails();
        doAnswer(invocation ->
        {
            MusicService.OutputAdapter output = invocation.getArgument(4);
            output.replyError("Playlist could not be read.");
            return null;
        }).when(fixture.getMusicService()).queuePlaylist(any(), any(), eq("favorite"), any(), any());
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());
        render.get().accept(FAVORITE_PAGE);

        assertEquals(List.of("deferEdit"), acks.acks());
        assertEphemeralFollowup(acks, "Playlist could not be read.");
        verify(acks.hook()).editOriginalEmbeds(any(MessageEmbed[].class));
    }

    @Test
    @DisplayName("playlistdetails queue (one track): load result after the re-render goes through the hook")
    void playlistDetailsQueueTrack_renderThenLoaded_acknowledgesOnce()
    {
        fixture.withButtonId("playlistdetails_1_1_queue_1_2_" + fixture.getUser().getIdLong())
                .withMemberInVoiceChannel();
        AtomicReference<Consumer<MusicService.PlaylistTracksPageInfo>> render = stubFavoriteDetails();
        when(fixture.getMusicService().getPlaylistTrackUrlAtPosition(any(MusicService.PlaylistDraftContext.class), eq(2)))
                .thenReturn("https://example/2");
        AtomicReference<MusicService.OutputAdapter> output = new AtomicReference<>();
        doAnswer(invocation ->
        {
            output.set(invocation.getArgument(4));
            return null;
        }).when(fixture.getMusicService()).play(any(), any(), eq("https://example/2"), any(), any());
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());
        render.get().accept(FAVORITE_PAGE);
        output.get().replySuccess("Added daylight to the queue");

        assertEquals(List.of("deferEdit"), acks.acks());
        assertEphemeralFollowup(acks, "Added daylight to the queue");
    }

    @Test
    @DisplayName("playlistdetails next: acknowledges before the track page loads (no 3-second timeout)")
    void playlistDetailsNext_defersBeforeLoadingTracks()
    {
        fixture.withButtonId("playlistdetails_1_1_next_1_0_" + fixture.getUser().getIdLong());
        AtomicReference<Consumer<MusicService.PlaylistTracksPageInfo>> render = stubFavoriteDetails();
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        assertEquals(List.of("deferEdit"), acks.acks(), "must acknowledge before the page's tracks finish loading");
        render.get().accept(FAVORITE_PAGE);
        assertEquals(List.of("deferEdit"), acks.acks());
        verify(acks.hook()).editOriginalEmbeds(any(MessageEmbed[].class));
    }

    @Test
    @DisplayName("playlistdetails move: acknowledges before the track page loads, then shows the menu through the hook")
    void playlistDetailsMove_defersBeforeLoadingTracks()
    {
        fixture.withButtonId("playlistdetails_1_1_move_1_2_" + fixture.getUser().getIdLong());
        AtomicReference<Consumer<MusicService.PlaylistTracksPageInfo>> render = stubFavoriteDetails();
        when(fixture.getMusicService().canEditPlaylistEntries(eq(fixture.getGuild()), eq(fixture.getMember())))
                .thenReturn(true);
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        assertEquals(List.of("deferEdit"), acks.acks(), "must acknowledge before the page's tracks finish loading");
        render.get().accept(FAVORITE_PAGE);
        assertEquals(List.of("deferEdit"), acks.acks());
        verify(acks.hook()).editOriginalEmbeds(any(MessageEmbed[].class));
        verify(acks.hookEdit()).setComponents(
                argThat((java.util.Collection<? extends net.dv8tion.jda.api.components.MessageTopLevelComponent> rows)
                        -> rows.size() == 2));
        verify(acks.hookEdit()).queue();
    }

    @Test
    @DisplayName("playlistdetails move: a playlist that vanished while loading gets an ephemeral follow-up")
    void playlistDetailsMove_missingPlaylist_ephemeralFollowup()
    {
        fixture.withButtonId("playlistdetails_1_1_move_1_2_" + fixture.getUser().getIdLong());
        AtomicReference<Consumer<MusicService.PlaylistTracksPageInfo>> render = stubFavoriteDetails();
        when(fixture.getMusicService().canEditPlaylistEntries(eq(fixture.getGuild()), eq(fixture.getMember())))
                .thenReturn(true);
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());
        render.get().accept(null);

        assertEquals(List.of("deferEdit"), acks.acks());
        assertEphemeralFollowup(acks, "Playlist no longer exists. Click Back and Refresh.");
    }

    @Test
    @DisplayName("playlists playnow (list view): embed refresh and later playlist-loaded reply acknowledge once")
    void playlistsPlayNow_refreshThenLoaded_acknowledgesOnce()
    {
        fixture.withButtonId("playlists_playnow_1_1_" + fixture.getUser().getIdLong())
                .withMemberInVoiceChannel();
        when(fixture.getMusicService().getAvailablePlaylistNames())
                .thenReturn(MusicService.PlaylistNamesInfo.success(List.of("favorite")));
        stubChannel();
        AtomicReference<MusicService.OutputAdapter> output = new AtomicReference<>();
        doAnswer(invocation ->
        {
            output.set(invocation.getArgument(4));
            return null;
        }).when(fixture.getMusicService()).playPlaylistNow(any(), any(), eq("favorite"), any(), any());
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());
        output.get().replySuccess("Now playing playlist `favorite` (queue replaced).");

        assertEquals(List.of("deferEdit"), acks.acks());
        verify(acks.hook()).editOriginalEmbeds(any(MessageEmbed[].class));
        assertEphemeralFollowup(acks, "Now playing playlist `favorite` (queue replaced).");
    }

    @Test
    @DisplayName("playlists queue (list view): an immediate error reply leaves one acknowledgement")
    void playlistsQueue_immediateError_acknowledgesOnce()
    {
        fixture.withButtonId("playlists_queue_1_1_" + fixture.getUser().getIdLong())
                .withMemberInVoiceChannel();
        when(fixture.getMusicService().getAvailablePlaylistNames())
                .thenReturn(MusicService.PlaylistNamesInfo.success(List.of("favorite")));
        stubChannel();
        doAnswer(invocation ->
        {
            MusicService.OutputAdapter output = invocation.getArgument(4);
            output.replyError("Playlist could not be read.");
            return null;
        }).when(fixture.getMusicService()).queuePlaylist(any(), any(), eq("favorite"), any(), any());
        InteractionAckTracker acks = InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        assertEquals(List.of("deferEdit"), acks.acks());
        assertEphemeralFollowup(acks, "Playlist could not be read.");
    }
}
