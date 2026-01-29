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

import com.jagrosh.jmusicbot.commands.v2.music.SearchSlashCmd;
import com.jagrosh.jmusicbot.service.SearchService;
import com.jagrosh.jmusicbot.testutil.commands.SlashCommandTestFixture;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SearchSlashCmd}.
 * Uses SlashCommandTestFixture for cleaner, more maintainable tests.
 */
public class SearchSlashCmdTest
{
    private SlashCommandTestFixture fixture;
    private SearchSlashCmd command;

    @Mock
    private OptionMapping queryOption;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        fixture = SlashCommandTestFixture.create();
        fixture.withReplyQueueCallback();
        command = new SearchSlashCmd(fixture.getBot());
    }

    @Test
    void testDoCommand_CallsSearchService()
    {
        // Given
        String query = "test song";
        when(fixture.getEvent().getOption("query")).thenReturn(queryOption);
        when(queryOption.getAsString()).thenReturn(query);

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply("🔍 Searching YouTube for `" + query + "`...");
        verify(fixture.getSearchService()).search(
                eq(fixture.getGuild()),
                eq(fixture.getMember()),
                eq(query),
                eq("ytsearch:"),
                eq(fixture.getTextChannel()),
                any(SearchService.SearchCallback.class));
    }

    @Test
    void testDoCommand_OnTrackLoaded_EditsMessageWithSuccess()
    {
        // Given
        String query = "test song";
        when(fixture.getEvent().getOption("query")).thenReturn(queryOption);
        when(queryOption.getAsString()).thenReturn(query);

        ArgumentCaptor<SearchService.SearchCallback> callbackCaptor =
                ArgumentCaptor.forClass(SearchService.SearchCallback.class);

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getSearchService()).search(any(), any(), any(), any(), any(), callbackCaptor.capture());

        AudioTrack track = mock(AudioTrack.class);
        String formattedMessage = "Added to queue";
        callbackCaptor.getValue().onTrackLoaded(track, 1, formattedMessage);

        verify(fixture.getHook()).editOriginal("✅ " + formattedMessage);
    }

    @Test
    void testDoCommand_OnSearchResults_ShowsMenu()
    {
        // Given
        String query = "test song";
        when(fixture.getEvent().getOption("query")).thenReturn(queryOption);
        when(queryOption.getAsString()).thenReturn(query);
        when(fixture.getHook().editOriginalEmbeds(any(MessageEmbed.class))).thenReturn(fixture.getEditAction());
        when(fixture.getEditAction().setContent(anyString())).thenReturn(fixture.getEditAction());
        when(fixture.getEditAction().setComponents(any(net.dv8tion.jda.api.components.MessageTopLevelComponent.class)))
                .thenReturn(fixture.getEditAction());
        fixture.withEditQueueCallback();

        ArgumentCaptor<SearchService.SearchCallback> callbackCaptor =
                ArgumentCaptor.forClass(SearchService.SearchCallback.class);

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getSearchService()).search(any(), any(), any(), any(), any(), callbackCaptor.capture());

        AudioPlaylist playlist = mock(AudioPlaylist.class);
        AudioTrack track1 = mock(AudioTrack.class);
        AudioTrack track2 = mock(AudioTrack.class);
        AudioTrackInfo info1 = new AudioTrackInfo(
                "Song 1", "Author", 1000, "identifier", false,
                "https://www.youtube.com/watch?v=1");
        AudioTrackInfo info2 = new AudioTrackInfo(
                "Song 2", "Author", 1000, "identifier", false,
                "https://www.youtube.com/watch?v=2");

        when(track1.getInfo()).thenReturn(info1);
        when(track2.getInfo()).thenReturn(info2);
        when(track1.getDuration()).thenReturn(180000L);
        when(track2.getDuration()).thenReturn(240000L);
        when(playlist.getTracks()).thenReturn(List.of(track1, track2));

        callbackCaptor.getValue().onSearchResults(playlist, new String[]{});

        verify(fixture.getHook()).editOriginalEmbeds(any(MessageEmbed.class));
        verify(fixture.getEditAction()).setContent("");
        verify(fixture.getEditAction()).setComponents(any(net.dv8tion.jda.api.components.MessageTopLevelComponent.class));
    }

    @Test
    void testDoCommand_OnSearchResultsEmpty_ShowsNoResults()
    {
        // Given
        String query = "nonexistent";
        when(fixture.getEvent().getOption("query")).thenReturn(queryOption);
        when(queryOption.getAsString()).thenReturn(query);

        ArgumentCaptor<SearchService.SearchCallback> callbackCaptor =
                ArgumentCaptor.forClass(SearchService.SearchCallback.class);

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getSearchService()).search(any(), any(), any(), any(), any(), callbackCaptor.capture());

        AudioPlaylist playlist = mock(AudioPlaylist.class);
        when(playlist.getTracks()).thenReturn(Collections.emptyList());

        callbackCaptor.getValue().onSearchResults(playlist, new String[]{});

        verify(fixture.getHook()).editOriginal("⚠️ No results found for `" + query + "`.");
    }

    @Test
    void testDoCommand_OnNoMatches_ShowsWarning()
    {
        // Given
        String query = "nonexistent";
        when(fixture.getEvent().getOption("query")).thenReturn(queryOption);
        when(queryOption.getAsString()).thenReturn(query);

        ArgumentCaptor<SearchService.SearchCallback> callbackCaptor =
                ArgumentCaptor.forClass(SearchService.SearchCallback.class);

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getSearchService()).search(any(), any(), any(), any(), any(), callbackCaptor.capture());

        callbackCaptor.getValue().onNoMatches(query);

        verify(fixture.getHook()).editOriginal("⚠️ No results found for `" + query + "`.");
    }

    @Test
    void testDoCommand_OnLoadFailed_ShowsError()
    {
        // Given
        String query = "test song";
        String errorMessage = "Failed to load";
        when(fixture.getEvent().getOption("query")).thenReturn(queryOption);
        when(queryOption.getAsString()).thenReturn(query);

        ArgumentCaptor<SearchService.SearchCallback> callbackCaptor =
                ArgumentCaptor.forClass(SearchService.SearchCallback.class);

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getSearchService()).search(any(), any(), any(), any(), any(), callbackCaptor.capture());

        callbackCaptor.getValue().onLoadFailed(errorMessage);

        verify(fixture.getHook()).editOriginal("❌ " + errorMessage);
    }

    @Test
    void testDoCommand_OnError_ShowsError()
    {
        // Given
        String query = "test song";
        String errorMessage = "An error occurred";
        when(fixture.getEvent().getOption("query")).thenReturn(queryOption);
        when(queryOption.getAsString()).thenReturn(query);

        ArgumentCaptor<SearchService.SearchCallback> callbackCaptor =
                ArgumentCaptor.forClass(SearchService.SearchCallback.class);

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getSearchService()).search(any(), any(), any(), any(), any(), callbackCaptor.capture());

        callbackCaptor.getValue().onError(errorMessage);

        verify(fixture.getHook()).editOriginal("❌ " + errorMessage);
    }

    @Test
    void testDoCommand_OnSearchResultsWithSelection_AddsTrackToQueue()
    {
        // Given
        String query = "test song";
        when(fixture.getEvent().getOption("query")).thenReturn(queryOption);
        when(queryOption.getAsString()).thenReturn(query);
        when(fixture.getHook().editOriginalEmbeds(any(MessageEmbed.class))).thenReturn(fixture.getEditAction());
        when(fixture.getEditAction().setContent(anyString())).thenReturn(fixture.getEditAction());
        when(fixture.getEditAction().setComponents(any(net.dv8tion.jda.api.components.MessageTopLevelComponent.class)))
                .thenReturn(fixture.getEditAction());
        fixture.withEditQueueCallback();
        when(fixture.getMessage().getIdLong()).thenReturn(67890L);

        ArgumentCaptor<SearchService.SearchCallback> callbackCaptor =
                ArgumentCaptor.forClass(SearchService.SearchCallback.class);

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getSearchService()).search(any(), any(), any(), any(), any(), callbackCaptor.capture());

        AudioPlaylist playlist = mock(AudioPlaylist.class);
        AudioTrack track1 = mock(AudioTrack.class);
        AudioTrackInfo info1 = new AudioTrackInfo(
                "Song 1", "Author", 1000, "identifier", false,
                "https://www.youtube.com/watch?v=1");

        when(track1.getInfo()).thenReturn(info1);
        when(track1.getDuration()).thenReturn(180000L);
        when(playlist.getTracks()).thenReturn(List.of(track1));

        callbackCaptor.getValue().onSearchResults(playlist, new String[]{});

        // Verify waiter was set up
        verify(fixture.getEventWaiter()).waitForEvent(
                eq(StringSelectInteractionEvent.class), any(), any(), anyLong(), any(), any());
    }
}
