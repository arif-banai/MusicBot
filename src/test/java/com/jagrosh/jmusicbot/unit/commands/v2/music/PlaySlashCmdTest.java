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

import com.jagrosh.jmusicbot.commands.v2.music.PlaySlashCmd;
import com.jagrosh.jmusicbot.testutil.commands.SlashCommandTestFixture;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link PlaySlashCmd}.
 * Uses SlashCommandTestFixture for cleaner, more maintainable tests.
 */
public class PlaySlashCmdTest
{
    private SlashCommandTestFixture fixture;
    private PlaySlashCmd command;

    @Mock
    private OptionMapping queryOption;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        fixture = SlashCommandTestFixture.create();
        fixture.withReplyQueueCallback();
        command = new PlaySlashCmd(fixture.getBot());
    }

    @Test
    void testDoCommand_WithQuery_CallsMusicServicePlay()
    {
        // Given
        String query = "test song";
        when(fixture.getEvent().getOption("query")).thenReturn(queryOption);
        when(queryOption.getAsString()).thenReturn(query);

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getEvent()).reply("⏳ Loading... `[" + query + "]`");
        verify(fixture.getMusicService()).play(
                eq(fixture.getGuild()),
                eq(fixture.getMember()),
                eq(query),
                eq(fixture.getTextChannel()),
                any());
    }

    @Test
    void testDoCommand_WithoutQuery_CallsMusicServicePlayWithEmptyString()
    {
        // Given
        when(fixture.getEvent().getOption("query")).thenReturn(null);

        // When
        command.doCommand(fixture.getEvent());

        // Then
        verify(fixture.getMusicService()).play(
                eq(fixture.getGuild()),
                eq(fixture.getMember()),
                eq(""),
                eq(fixture.getTextChannel()),
                any());
    }

    @Test
    void testOnAutoComplete_EmptyInput_RepliesEmptyChoices()
    {
        // Given
        when(fixture.getFocusedOption().getValue()).thenReturn("");

        // When
        command.onAutoComplete(fixture.getAutoCompleteEvent());

        // Then
        verify(fixture.getAutoCompleteEvent()).replyChoices();
        verify(fixture.getAutoCompleteCallback()).queue();
        verify(fixture.getPlayerManager(), never()).loadItemOrdered(any(), anyString(), any());
    }

    @Test
    void testOnAutoComplete_HttpUrl_RepliesWithUrlAsChoice()
    {
        // Given
        String url = "https://www.youtube.com/watch?v=test";
        when(fixture.getFocusedOption().getValue()).thenReturn(url);
        when(fixture.getAutoCompleteEvent().replyChoices(any(Command.Choice.class)))
                .thenReturn(fixture.getAutoCompleteCallback());

        // When
        command.onAutoComplete(fixture.getAutoCompleteEvent());

        // Then
        ArgumentCaptor<Command.Choice> choiceCaptor = ArgumentCaptor.forClass(Command.Choice.class);
        verify(fixture.getAutoCompleteEvent()).replyChoices(choiceCaptor.capture());
        Command.Choice capturedChoice = choiceCaptor.getValue();
        assertEquals(url, capturedChoice.getName());
        assertEquals(url, capturedChoice.getAsString());
        verify(fixture.getPlayerManager(), never()).loadItemOrdered(any(), anyString(), any());
    }

    @Test
    void testOnAutoComplete_WindowsPath_RepliesWithPathAsChoice()
    {
        // Given
        String path = "C:\\Music\\song.mp3";
        when(fixture.getFocusedOption().getValue()).thenReturn(path);
        when(fixture.getAutoCompleteEvent().replyChoices(any(Command.Choice.class)))
                .thenReturn(fixture.getAutoCompleteCallback());

        // When
        command.onAutoComplete(fixture.getAutoCompleteEvent());

        // Then
        ArgumentCaptor<Command.Choice> choiceCaptor = ArgumentCaptor.forClass(Command.Choice.class);
        verify(fixture.getAutoCompleteEvent()).replyChoices(choiceCaptor.capture());
        Command.Choice capturedChoice = choiceCaptor.getValue();
        assertEquals(path, capturedChoice.getName());
        assertEquals(path, capturedChoice.getAsString());
    }

    @Test
    void testOnAutoComplete_UnixPath_RepliesWithPathAsChoice()
    {
        // Given
        String path = "/home/user/music/song.mp3";
        when(fixture.getFocusedOption().getValue()).thenReturn(path);
        when(fixture.getAutoCompleteEvent().replyChoices(any(Command.Choice.class)))
                .thenReturn(fixture.getAutoCompleteCallback());

        // When
        command.onAutoComplete(fixture.getAutoCompleteEvent());

        // Then
        ArgumentCaptor<Command.Choice> choiceCaptor = ArgumentCaptor.forClass(Command.Choice.class);
        verify(fixture.getAutoCompleteEvent()).replyChoices(choiceCaptor.capture());
        Command.Choice capturedChoice = choiceCaptor.getValue();
        assertEquals(path, capturedChoice.getName());
        assertEquals(path, capturedChoice.getAsString());
    }

    @Test
    void testOnAutoComplete_SearchQuery_LoadsItemAndRepliesWithResults()
    {
        // Given
        String query = "test song";
        when(fixture.getFocusedOption().getValue()).thenReturn(query);
        when(fixture.getAutoCompleteEvent().replyChoices(anyList()))
                .thenReturn(fixture.getAutoCompleteCallback());
        when(fixture.getAutoCompleteEvent().replyChoices(any(Command.Choice.class)))
                .thenReturn(fixture.getAutoCompleteCallback());

        ArgumentCaptor<AudioLoadResultHandler> handlerCaptor = 
                ArgumentCaptor.forClass(AudioLoadResultHandler.class);

        // When
        command.onAutoComplete(fixture.getAutoCompleteEvent());

        // Then
        verify(fixture.getPlayerManager()).loadItemOrdered(
                eq(fixture.getGuild()), eq("ytsearch:" + query), handlerCaptor.capture());

        // Simulate track loaded
        AudioTrack track = mock(AudioTrack.class);
        AudioTrackInfo trackInfo = new AudioTrackInfo(
                "Test Song", "Author", 1000, "identifier", false, 
                "https://www.youtube.com/watch?v=test");
        when(track.getInfo()).thenReturn(trackInfo);

        handlerCaptor.getValue().trackLoaded(track);
        
        ArgumentCaptor<Command.Choice> choiceCaptor = ArgumentCaptor.forClass(Command.Choice.class);
        verify(fixture.getAutoCompleteEvent()).replyChoices(choiceCaptor.capture());
        Command.Choice capturedChoice = choiceCaptor.getValue();
        assertEquals("Test Song", capturedChoice.getName());
        assertEquals("https://www.youtube.com/watch?v=test", capturedChoice.getAsString());
    }

    @Test
    void testOnAutoComplete_PlaylistLoaded_RepliesWithMultipleChoices()
    {
        // Given
        String query = "test playlist";
        when(fixture.getFocusedOption().getValue()).thenReturn(query);
        when(fixture.getAutoCompleteEvent().replyChoices(anyList()))
                .thenReturn(fixture.getAutoCompleteCallback());

        ArgumentCaptor<AudioLoadResultHandler> handlerCaptor = 
                ArgumentCaptor.forClass(AudioLoadResultHandler.class);

        // When
        command.onAutoComplete(fixture.getAutoCompleteEvent());

        // Then
        verify(fixture.getPlayerManager()).loadItemOrdered(
                eq(fixture.getGuild()), eq("ytsearch:" + query), handlerCaptor.capture());

        // Simulate playlist loaded
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
        when(playlist.getTracks()).thenReturn(List.of(track1, track2));

        handlerCaptor.getValue().playlistLoaded(playlist);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Command.Choice>> choicesCaptor = 
                ArgumentCaptor.forClass((Class<List<Command.Choice>>) (Class<?>) List.class);
        verify(fixture.getAutoCompleteEvent()).replyChoices(choicesCaptor.capture());
        List<Command.Choice> capturedChoices = choicesCaptor.getValue();
        assertEquals(2, capturedChoices.size());
    }

    @Test
    void testOnAutoComplete_NoMatches_RepliesEmptyChoices()
    {
        // Given
        String query = "nonexistent song";
        when(fixture.getFocusedOption().getValue()).thenReturn(query);

        ArgumentCaptor<AudioLoadResultHandler> handlerCaptor = 
                ArgumentCaptor.forClass(AudioLoadResultHandler.class);

        // When
        command.onAutoComplete(fixture.getAutoCompleteEvent());

        // Then
        verify(fixture.getPlayerManager()).loadItemOrdered(
                eq(fixture.getGuild()), eq("ytsearch:" + query), handlerCaptor.capture());

        handlerCaptor.getValue().noMatches();
        verify(fixture.getAutoCompleteEvent()).replyChoices();
    }

    @Test
    void testOnAutoComplete_LoadFailed_RepliesEmptyChoices()
    {
        // Given
        String query = "test song";
        when(fixture.getFocusedOption().getValue()).thenReturn(query);

        ArgumentCaptor<AudioLoadResultHandler> handlerCaptor = 
                ArgumentCaptor.forClass(AudioLoadResultHandler.class);

        // When
        command.onAutoComplete(fixture.getAutoCompleteEvent());

        // Then
        verify(fixture.getPlayerManager()).loadItemOrdered(
                eq(fixture.getGuild()), eq("ytsearch:" + query), handlerCaptor.capture());

        com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception = 
                mock(com.sedmelluq.discord.lavaplayer.tools.FriendlyException.class);
        handlerCaptor.getValue().loadFailed(exception);
        verify(fixture.getAutoCompleteEvent()).replyChoices();
    }
}
