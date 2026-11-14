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

import com.jagrosh.jmusicbot.listener.HistoryInteractionListener;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("HistoryInteractionListener Tests")
public class HistoryInteractionListenerTest {

    private ListenerTestFixture fixture;
    private HistoryInteractionListener listener;

    @BeforeEach
    void setUp() {
        fixture = ListenerTestFixture.create();
        listener = new HistoryInteractionListener(fixture.getBot());
        when(fixture.getButtonInteractionEvent().getUser()).thenReturn(fixture.getUser());
    }

    @Test
    @DisplayName("onButtonInteraction() handles history_ button with invalid format and replies error")
    void onButtonInteraction_historyInvalidFormat_repliesError() {
        fixture.withButtonId("history_ab");

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        verify(fixture.getButtonInteractionEvent()).reply(argThat((String s) -> s.contains("Invalid button state")));
        verify(fixture.getReplyAction()).setEphemeral(true);
        verify(fixture.getMusicService(), never()).stop(any(), any(), any());
    }

    @Test
    @DisplayName("onButtonInteraction() rejects select action not present on current page")
    void onButtonInteraction_selectOutsidePage_repliesError() {
        fixture.withButtonId("history_select11_1_0_" + fixture.getUser().getIdLong());
        MusicService.HistoryInfo info = new MusicService.HistoryInfo(
                new String[]{"1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11"},
                0L,
                50
        );
        when(fixture.getMusicService().getHistoryInfo(fixture.getGuild(), fixture.getJda())).thenReturn(info);

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        verify(fixture.getButtonInteractionEvent()).reply(argThat((String s) -> s.contains("isn't on this page")));
        verify(fixture.getReplyAction()).setEphemeral(true);
    }

    @Test
    @DisplayName("onButtonInteraction() shows disabled message when history is disabled")
    void onButtonInteraction_historyDisabled_showsDisabledMessage() {
        fixture.withButtonId("history_prev_1_0_" + fixture.getUser().getIdLong());
        MusicService.HistoryInfo info = new MusicService.HistoryInfo(new String[0], 0L, 0, true);
        when(fixture.getMusicService().getHistoryInfo(fixture.getGuild(), fixture.getJda())).thenReturn(info);
        MessageEditCallbackAction editAction = mock(MessageEditCallbackAction.class);
        when(fixture.getButtonInteractionEvent().editMessage(any(String.class))).thenReturn(editAction);
        when(editAction.setEmbeds()).thenReturn(editAction);
        when(editAction.setComponents()).thenReturn(editAction);

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        verify(fixture.getButtonInteractionEvent()).editMessage(argThat((String s) -> s.contains("disabled by config")));
    }

    // ==================== Acknowledgement: exactly once, later output via the hook ====================

    private InteractionAckTracker stubHistoryAction(String action) {
        fixture.withButtonId("history_" + action + "_1_2_" + fixture.getUser().getIdLong())
                .withMemberInVoiceChannel();
        MusicService.HistoryInfo info = new MusicService.HistoryInfo(new String[]{"one", "two", "three"}, 0L, 50);
        when(fixture.getMusicService().getHistoryInfo(fixture.getGuild(), fixture.getJda())).thenReturn(info);
        MessageChannelUnion channelUnion = mock(MessageChannelUnion.class);
        when(channelUnion.asGuildMessageChannel()).thenReturn((GuildMessageChannelUnion) fixture.getTextChannel());
        when(fixture.getButtonInteractionEvent().getChannel()).thenReturn(channelUnion);
        return InteractionAckTracker.attach(fixture.getButtonInteractionEvent(), fixture.getReplyAction());
    }

    private void assertOneAckAndHookOutput(InteractionAckTracker acks, String content) {
        assertEquals(List.of("deferEdit"), acks.acks());
        verify(acks.hook()).sendMessage(content);
        verify(acks.followup()).setEphemeral(true);
        verify(acks.followup()).queue();
        verify(acks.hook()).editOriginalEmbeds(any(MessageEmbed[].class));
        verify(fixture.getButtonInteractionEvent(), never()).reply(anyString());
    }

    @Test
    @DisplayName("history queue: the result reply and the embed refresh acknowledge once")
    void onButtonInteraction_queue_acknowledgesOnce() {
        InteractionAckTracker acks = stubHistoryAction("queue");
        doAnswer(invocation -> {
            MusicService.OutputAdapter output = invocation.getArgument(4);
            output.replySuccess("Added two to the queue");
            return null;
        }).when(fixture.getMusicService()).queueFromHistory(any(), any(), eq(2), any(), any());

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        assertOneAckAndHookOutput(acks, "Added two to the queue");
    }

    @Test
    @DisplayName("history playnow: the result reply and the embed refresh acknowledge once")
    void onButtonInteraction_playNow_acknowledgesOnce() {
        InteractionAckTracker acks = stubHistoryAction("playnow");
        doAnswer(invocation -> {
            MusicService.OutputAdapter output = invocation.getArgument(4);
            output.replySuccess("Now playing two");
            return null;
        }).when(fixture.getMusicService()).playFromHistoryNow(any(), any(), eq(2), any(), any());

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        assertOneAckAndHookOutput(acks, "Now playing two");
    }

    @Test
    @DisplayName("history queueall: the result reply and the embed refresh acknowledge once")
    void onButtonInteraction_queueAll_acknowledgesOnce() {
        InteractionAckTracker acks = stubHistoryAction("queueall");
        doAnswer(invocation -> {
            MusicService.OutputAdapter output = invocation.getArgument(3);
            output.replySuccess("Queued 3 tracks from history");
            return null;
        }).when(fixture.getMusicService()).queueAllFromHistory(any(), any(), any(), any());

        listener.onButtonInteraction(fixture.getButtonInteractionEvent());

        assertOneAckAndHookOutput(acks, "Queued 3 tracks from history");
    }
}
