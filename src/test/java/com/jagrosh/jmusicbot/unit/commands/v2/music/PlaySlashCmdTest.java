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
        verify(fixture.getPlayerManager(), never()).loadItem(anyString(), any());
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
        verify(fixture.getPlayerManager(), never()).loadItem(anyString(), any());
    }

    @Test
    void testOnAutoComplete_SchemeLessYoutubeLink_RepliesWithHttpsLinkAsChoice()
    {
        // Given
        String input = "youtube.com/watch?v=IY1oU1G2DA0";
        when(fixture.getFocusedOption().getValue()).thenReturn(input);
        when(fixture.getAutoCompleteEvent().replyChoices(any(Command.Choice.class)))
                .thenReturn(fixture.getAutoCompleteCallback());

        // When
        command.onAutoComplete(fixture.getAutoCompleteEvent());

        // Then
        ArgumentCaptor<Command.Choice> choiceCaptor = ArgumentCaptor.forClass(Command.Choice.class);
        verify(fixture.getAutoCompleteEvent()).replyChoices(choiceCaptor.capture());
        Command.Choice capturedChoice = choiceCaptor.getValue();
        String expected = "https://youtube.com/watch?v=IY1oU1G2DA0";
        assertEquals(expected, capturedChoice.getName());
        assertEquals(expected, capturedChoice.getAsString());
        verify(fixture.getPlayerManager(), never()).loadItem(anyString(), any());
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
        verify(fixture.getPlayerManager()).loadItem(eq("ytsearch:" + query), handlerCaptor.capture());

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
        verify(fixture.getPlayerManager()).loadItem(eq("ytsearch:" + query), handlerCaptor.capture());

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
        verify(fixture.getPlayerManager()).loadItem(eq("ytsearch:" + query), handlerCaptor.capture());

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
        verify(fixture.getPlayerManager()).loadItem(eq("ytsearch:" + query), handlerCaptor.capture());

        com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception = 
                mock(com.sedmelluq.discord.lavaplayer.tools.FriendlyException.class);
        handlerCaptor.getValue().loadFailed(exception);
        verify(fixture.getAutoCompleteEvent()).replyChoices();
    }

    @Test
    void testOnAutoComplete_Search_DoesNotQueueBehindTheGuildsPlayLoads()
    {
        // Given: MusicService.play loads in order on the guild key; autocomplete searches on that key
        // would run one after another, so a real /play would wait behind every abandoned keystroke.
        when(fixture.getFocusedOption().getValue()).thenReturn("some song");

        // When
        command.onAutoComplete(fixture.getAutoCompleteEvent());

        // Then: an unordered load
        verify(fixture.getPlayerManager()).loadItem(eq("ytsearch:some song"), any(AudioLoadResultHandler.class));
        verify(fixture.getPlayerManager(), never()).loadItemOrdered(any(), anyString(), any());
    }

    @Test
    void testOnAutoComplete_LinkLongerThanAChoiceValue_RepliesEmptyChoices()
    {
        // Given: a scheme-less link that normalization ("https://" prefix) pushes past Discord's
        // 100-character limit for a choice value
        String input = "youtube.com/watch?v=IY1oU1G2DA0&list=" + "a".repeat(58);
        assertTrue(input.length() <= 100 && input.length() + "https://".length() > 100);
        when(fixture.getFocusedOption().getValue()).thenReturn(input);

        // When
        assertDoesNotThrow(() -> command.onAutoComplete(fixture.getAutoCompleteEvent()));

        // Then: no direct-link choice (it would be rejected), but the interaction is still answered
        verify(fixture.getAutoCompleteEvent(), never()).replyChoices(any(Command.Choice.class));
        verify(fixture.getAutoCompleteEvent()).replyChoices();
        verify(fixture.getAutoCompleteCallback()).queue();
        verify(fixture.getPlayerManager(), never()).loadItem(anyString(), any());
    }

    @Test
    void testOnAutoComplete_LinkOfExactlyOneHundredChars_RepliesWithIt()
    {
        // Given
        String prefix = "https://www.youtube.com/watch?v=IY1oU1G2DA0&list=";
        String url = prefix + "b".repeat(100 - prefix.length());
        assertEquals(100, url.length());
        when(fixture.getFocusedOption().getValue()).thenReturn(url);
        when(fixture.getAutoCompleteEvent().replyChoices(any(Command.Choice.class)))
                .thenReturn(fixture.getAutoCompleteCallback());

        // When
        command.onAutoComplete(fixture.getAutoCompleteEvent());

        // Then
        ArgumentCaptor<Command.Choice> choiceCaptor = ArgumentCaptor.forClass(Command.Choice.class);
        verify(fixture.getAutoCompleteEvent()).replyChoices(choiceCaptor.capture());
        assertEquals(url, choiceCaptor.getValue().getName());
        assertEquals(url, choiceCaptor.getValue().getAsString());
    }

    // ==================== Acknowledgement deadlines ====================

    @Test
    void testOnAutoComplete_SlowSearch_RepliesEmptyChoicesBeforeTheThreeSecondDeadline()
    {
        // Given: a search that has not finished when the fallback fires (E6)
        String query = "slow search";
        when(fixture.getFocusedOption().getValue()).thenReturn(query);
        when(fixture.getAutoCompleteEvent().replyChoices(any(Command.Choice.class)))
                .thenReturn(fixture.getAutoCompleteCallback());
        ArgumentCaptor<AudioLoadResultHandler> handlerCaptor =
                ArgumentCaptor.forClass(AudioLoadResultHandler.class);
        ArgumentCaptor<Runnable> fallbackCaptor = ArgumentCaptor.forClass(Runnable.class);
        ArgumentCaptor<Long> delayCaptor = ArgumentCaptor.forClass(Long.class);
        ArgumentCaptor<java.util.concurrent.TimeUnit> unitCaptor =
                ArgumentCaptor.forClass(java.util.concurrent.TimeUnit.class);

        // When
        command.onAutoComplete(fixture.getAutoCompleteEvent());

        // Then: a fallback is scheduled well inside Discord's 3-second window
        verify(fixture.getPlayerManager()).loadItem(eq("ytsearch:" + query), handlerCaptor.capture());
        verify(fixture.getThreadpool()).schedule(fallbackCaptor.capture(), delayCaptor.capture(), unitCaptor.capture());
        long delayMs = unitCaptor.getValue().toMillis(delayCaptor.getValue());
        assertTrue(delayMs > 0 && delayMs <= 2500, "fallback delay was " + delayMs + " ms");
        verify(fixture.getAutoCompleteEvent(), never()).replyChoices();

        // When the deadline passes first, then the search finishes
        fallbackCaptor.getValue().run();
        AudioTrack track = mock(AudioTrack.class);
        when(track.getInfo()).thenReturn(new AudioTrackInfo(
                "Late Song", "Author", 1000, "identifier", false, "https://www.youtube.com/watch?v=late"));
        handlerCaptor.getValue().trackLoaded(track);

        // Then: exactly one acknowledgement, the empty fallback
        verify(fixture.getAutoCompleteEvent()).replyChoices();
        verify(fixture.getAutoCompleteEvent(), never()).replyChoices(any(Command.Choice.class));
        verify(fixture.getAutoCompleteCallback(), times(1)).queue();
    }

    @Test
    void testOnAutoComplete_FastSearch_RepliesOnceAndCancelsTheFallback()
    {
        // Given
        String query = "fast search";
        when(fixture.getFocusedOption().getValue()).thenReturn(query);
        when(fixture.getAutoCompleteEvent().replyChoices(any(Command.Choice.class)))
                .thenReturn(fixture.getAutoCompleteCallback());
        ArgumentCaptor<AudioLoadResultHandler> handlerCaptor =
                ArgumentCaptor.forClass(AudioLoadResultHandler.class);
        ArgumentCaptor<Runnable> fallbackCaptor = ArgumentCaptor.forClass(Runnable.class);

        // When: the search finishes, then the (already cancelled) fallback still runs
        command.onAutoComplete(fixture.getAutoCompleteEvent());
        verify(fixture.getPlayerManager()).loadItem(eq("ytsearch:" + query), handlerCaptor.capture());
        verify(fixture.getThreadpool()).schedule(fallbackCaptor.capture(), anyLong(), any());
        AudioTrack track = mock(AudioTrack.class);
        when(track.getInfo()).thenReturn(new AudioTrackInfo(
                "Fast Song", "Author", 1000, "identifier", false, "https://www.youtube.com/watch?v=fast"));
        handlerCaptor.getValue().trackLoaded(track);
        fallbackCaptor.getValue().run();

        // Then
        verify(fixture.getScheduledFuture()).cancel(false);
        verify(fixture.getAutoCompleteEvent()).replyChoices(any(Command.Choice.class));
        verify(fixture.getAutoCompleteEvent(), never()).replyChoices();
        verify(fixture.getAutoCompleteCallback(), times(1)).queue();
    }

    @Test
    void testDoCommand_WithQuery_AcknowledgesBeforeLoadingAndLoadsThroughTheHook()
    {
        // Given
        String query = "test song";
        when(fixture.getEvent().getOption("query")).thenReturn(queryOption);
        when(queryOption.getAsString()).thenReturn(query);

        // When
        command.doCommand(fixture.getEvent());

        // Then: the "Loading..." reply is queued before any load starts, and output goes via the hook
        org.mockito.InOrder inOrder = inOrder(fixture.getEvent(), fixture.getReplyAction(), fixture.getMusicService());
        inOrder.verify(fixture.getEvent()).reply("⏳ Loading... `[" + query + "]`");
        inOrder.verify(fixture.getReplyAction()).queue(any());
        inOrder.verify(fixture.getMusicService()).play(any(), any(), eq(query), any(),
                any(com.jagrosh.jmusicbot.commands.v2.SlashOutputAdapters.InteractionHookOutputAdapter.class));
        verify(fixture.getEvent(), times(1)).reply(anyString());
    }
}
