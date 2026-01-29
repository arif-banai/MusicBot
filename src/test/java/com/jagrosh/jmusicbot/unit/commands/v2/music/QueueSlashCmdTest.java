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
package com.jagrosh.jmusicbot.unit.commands.v2.music;

import com.jagrosh.jmusicbot.commands.v2.music.QueueSlashCmd;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.testutil.commands.SlashCommandTestFixture;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link QueueSlashCmd}.
 * Uses SlashCommandTestFixture for cleaner, more maintainable tests.
 */
public class QueueSlashCmdTest
{
    private SlashCommandTestFixture fixture;
    private QueueSlashCmd command;

    @Mock
    private OptionMapping pageOption;
    @Mock
    private MessageCreateData noMusicMsg;
    @Mock
    private MessageCreateData nowPlayingMsg;
    @Mock
    private MessageEmbed embed;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        fixture = SlashCommandTestFixture.create();
        fixture.withReplyQueueCallback().withRetrieveQueueCallback();
        command = new QueueSlashCmd(fixture.getBot());
    }

    @Test
    void testDoCommand_EmptyQueue_ShowsNoMusicMessage()
    {
        // Given
        when(fixture.getEvent().getOption("page")).thenReturn(null);
        when(fixture.getMusicService().getQueueInfo(fixture.getGuild(), fixture.getJda())).thenReturn(null);

        when(noMusicMsg.getEmbeds()).thenReturn(Collections.singletonList(embed));
        MusicService.NowPlayingInfo npInfo = new MusicService.NowPlayingInfo(null, noMusicMsg, false);
        when(fixture.getMusicService().getNowPlayingInfo(fixture.getGuild(), fixture.getJda())).thenReturn(npInfo);
        when(fixture.getEvent().reply(any(MessageCreateData.class))).thenReturn(fixture.getReplyAction());

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply(any(MessageCreateData.class));
    }

    @Test
    void testDoCommand_EmptyQueueWithPlaying_ShowsNowPlayingMessage()
    {
        // Given
        when(fixture.getEvent().getOption("page")).thenReturn(null);
        when(fixture.getMusicService().getQueueInfo(fixture.getGuild(), fixture.getJda())).thenReturn(null);

        when(nowPlayingMsg.getEmbeds()).thenReturn(Collections.singletonList(embed));
        MusicService.NowPlayingInfo npInfo = new MusicService.NowPlayingInfo(nowPlayingMsg, null, true);
        when(fixture.getMusicService().getNowPlayingInfo(fixture.getGuild(), fixture.getJda())).thenReturn(npInfo);
        when(fixture.getEvent().reply(any(MessageCreateData.class))).thenReturn(fixture.getReplyAction());

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply(any(MessageCreateData.class));
        verify(fixture.getNowPlayingHandler()).setLastNPMessage(fixture.getMessage());
    }

    @Test
    void testDoCommand_EmptyQueueNoNowPlayingInfo_ShowsEphemeralWarning()
    {
        // Given
        when(fixture.getEvent().getOption("page")).thenReturn(null);
        when(fixture.getMusicService().getQueueInfo(fixture.getGuild(), fixture.getJda())).thenReturn(null);
        when(fixture.getMusicService().getNowPlayingInfo(fixture.getGuild(), fixture.getJda())).thenReturn(null);
        when(fixture.getReplyAction().setEphemeral(true)).thenReturn(fixture.getReplyAction());

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply(contains("There is no music in the queue"));
        verify(fixture.getReplyAction()).setEphemeral(true);
    }

    @Test
    void testDoCommand_WithQueue_ShowsFirstPage()
    {
        // Given
        when(fixture.getEvent().getOption("page")).thenReturn(null);
        MusicService.QueueInfo queueInfo = new MusicService.QueueInfo(
                new String[]{"Track 1", "Track 2", "Track 3"},
                300000L,
                "Now Playing",
                "✅",
                RepeatMode.OFF,
                QueueType.LINEAR,
                null,
                null
        );
        when(fixture.getMusicService().getQueueInfo(fixture.getGuild(), fixture.getJda())).thenReturn(queueInfo);
        when(fixture.getMusicService().formatQueueTitle(queueInfo, "✅")).thenReturn("✅ Queue");
        when(fixture.getReplyAction().addEmbeds(any(MessageEmbed.class))).thenReturn(fixture.getReplyAction());

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply("✅ Queue");
        verify(fixture.getReplyAction()).addEmbeds(any(MessageEmbed.class));
    }

    @Test
    void testDoCommand_WithQueueAndPageNumber_ShowsSpecifiedPage()
    {
        // Given
        int page = 2;
        when(fixture.getEvent().getOption("page")).thenReturn(pageOption);
        when(pageOption.getAsLong()).thenReturn((long) page);

        MusicService.QueueInfo queueInfo = new MusicService.QueueInfo(
                new String[]{"Track 1", "Track 2", "Track 3", "Track 4", "Track 5",
                        "Track 6", "Track 7", "Track 8", "Track 9", "Track 10",
                        "Track 11", "Track 12"},
                600000L,
                "Now Playing",
                "✅",
                RepeatMode.OFF,
                QueueType.LINEAR,
                null,
                null
        );
        when(fixture.getMusicService().getQueueInfo(fixture.getGuild(), fixture.getJda())).thenReturn(queueInfo);
        when(fixture.getMusicService().formatQueueTitle(queueInfo, "✅")).thenReturn("✅ Queue");
        when(fixture.getReplyAction().addEmbeds(any(MessageEmbed.class))).thenReturn(fixture.getReplyAction());

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply("✅ Queue");
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(fixture.getReplyAction()).addEmbeds(embedCaptor.capture());
        assertTrue(embedCaptor.getValue().getTitle().contains("Page 2"));
    }

    @Test
    void testDoCommand_PageNumberExceedsTotalPages_ShowsLastPage()
    {
        // Given
        int page = 5;
        when(fixture.getEvent().getOption("page")).thenReturn(pageOption);
        when(pageOption.getAsLong()).thenReturn((long) page);

        MusicService.QueueInfo queueInfo = new MusicService.QueueInfo(
                new String[]{"Track 1", "Track 2", "Track 3"},
                300000L,
                "Now Playing",
                "✅",
                RepeatMode.OFF,
                QueueType.LINEAR,
                null,
                null
        );
        when(fixture.getMusicService().getQueueInfo(fixture.getGuild(), fixture.getJda())).thenReturn(queueInfo);
        when(fixture.getMusicService().formatQueueTitle(queueInfo, "✅")).thenReturn("✅ Queue");
        when(fixture.getReplyAction().addEmbeds(any(MessageEmbed.class))).thenReturn(fixture.getReplyAction());

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply("✅ Queue");
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(fixture.getReplyAction()).addEmbeds(embedCaptor.capture());
        assertTrue(embedCaptor.getValue().getTitle().contains("Page 1")); // Should clamp to last page (1)
    }

    @Test
    void testDoCommand_QueueWithManyTracks_PaginatesCorrectly()
    {
        // Given
        when(fixture.getEvent().getOption("page")).thenReturn(null);

        String[] tracks = new String[25]; // 25 tracks = 3 pages
        for (int i = 0; i < 25; i++)
        {
            tracks[i] = "Track " + (i + 1);
        }
        MusicService.QueueInfo queueInfo = new MusicService.QueueInfo(
                tracks,
                1500000L,
                "Now Playing",
                "✅",
                RepeatMode.OFF,
                QueueType.LINEAR,
                null,
                null
        );
        when(fixture.getMusicService().getQueueInfo(fixture.getGuild(), fixture.getJda())).thenReturn(queueInfo);
        when(fixture.getMusicService().formatQueueTitle(queueInfo, "✅")).thenReturn("✅ Queue");
        when(fixture.getReplyAction().addEmbeds(any(MessageEmbed.class))).thenReturn(fixture.getReplyAction());

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply("✅ Queue");
        ArgumentCaptor<MessageEmbed> embedCaptor = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(fixture.getReplyAction()).addEmbeds(embedCaptor.capture());
        MessageEmbed capturedEmbed = embedCaptor.getValue();
        assertTrue(capturedEmbed.getTitle().contains("Page 1/3"));
        assertTrue(capturedEmbed.getDescription().contains("Track 1"));
        assertTrue(capturedEmbed.getDescription().contains("Track 10"));
    }
}
