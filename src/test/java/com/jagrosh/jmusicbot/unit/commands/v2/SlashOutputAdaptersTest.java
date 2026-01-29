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
package com.jagrosh.jmusicbot.unit.commands.v2;

import com.jagrosh.jmusicbot.commands.v2.SlashOutputAdapters;
import com.jagrosh.jmusicbot.testutil.commands.SlashCommandTestFixture;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for {@link SlashOutputAdapters}.
 * Uses SlashCommandTestFixture for cleaner, more maintainable tests.
 */
public class SlashOutputAdaptersTest
{
    private SlashCommandTestFixture fixture;

    @Mock
    private MessageCreateData messageCreateData;
    @Mock
    private MessageEditData messageEditData;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        fixture = SlashCommandTestFixture.create();
    }

    // ==================== SlashEventOutputAdapter Tests ====================

    @Test
    void testSlashEventOutputAdapter_ReplySuccess()
    {
        // Given
        SlashOutputAdapters.SlashEventOutputAdapter adapter =
                new SlashOutputAdapters.SlashEventOutputAdapter(fixture.getEvent());

        // When
        adapter.replySuccess("Success message");

        // Then
        verify(fixture.getEvent()).reply("Success message");
        verify(fixture.getReplyAction()).queue();
    }

    @Test
    void testSlashEventOutputAdapter_ReplyError()
    {
        // Given
        when(fixture.getReplyAction().setEphemeral(anyBoolean())).thenReturn(fixture.getReplyAction());
        SlashOutputAdapters.SlashEventOutputAdapter adapter =
                new SlashOutputAdapters.SlashEventOutputAdapter(fixture.getEvent());

        // When
        adapter.replyError("Error message");

        // Then
        verify(fixture.getEvent()).reply("Error message");
        verify(fixture.getReplyAction()).setEphemeral(true);
        verify(fixture.getReplyAction()).queue();
    }

    @Test
    void testSlashEventOutputAdapter_ReplyWarning()
    {
        // Given
        when(fixture.getReplyAction().setEphemeral(anyBoolean())).thenReturn(fixture.getReplyAction());
        SlashOutputAdapters.SlashEventOutputAdapter adapter =
                new SlashOutputAdapters.SlashEventOutputAdapter(fixture.getEvent());

        // When
        adapter.replyWarning("Warning message");

        // Then
        verify(fixture.getEvent()).reply("Warning message");
        verify(fixture.getReplyAction()).setEphemeral(true);
        verify(fixture.getReplyAction()).queue();
    }

    @Test
    void testSlashEventOutputAdapter_EditMessage()
    {
        // Given
        SlashOutputAdapters.SlashEventOutputAdapter adapter =
                new SlashOutputAdapters.SlashEventOutputAdapter(fixture.getEvent());

        // When
        adapter.editMessage("Edit message");

        // Then
        verify(fixture.getEvent()).reply("Edit message");
        verify(fixture.getReplyAction()).queue();
    }

    @Test
    void testSlashEventOutputAdapter_EditMessageWithCallback()
    {
        // Given
        fixture.withReplyQueueCallback().withRetrieveQueueCallback();
        SlashOutputAdapters.SlashEventOutputAdapter adapter =
                new SlashOutputAdapters.SlashEventOutputAdapter(fixture.getEvent());
        @SuppressWarnings("unchecked")
        Consumer<net.dv8tion.jda.api.entities.Message> callback = mock(Consumer.class);

        // When
        adapter.editMessage("Edit message", callback);

        // Then
        verify(fixture.getEvent()).reply("Edit message");
        verify(callback).accept(fixture.getMessage());
    }

    @Test
    void testSlashEventOutputAdapter_EditNowPlaying()
    {
        // Given
        when(fixture.getAudioHandler().getNowPlaying(fixture.getJda())).thenReturn(messageCreateData);
        when(fixture.getEvent().reply(any(MessageCreateData.class))).thenReturn(fixture.getReplyAction());
        SlashOutputAdapters.SlashEventOutputAdapter adapter =
                new SlashOutputAdapters.SlashEventOutputAdapter(fixture.getEvent());

        // When
        adapter.editNowPlaying(fixture.getAudioHandler());

        // Then
        verify(fixture.getAudioHandler()).getNowPlaying(fixture.getJda());
        verify(fixture.getEvent()).reply(messageCreateData);
        verify(fixture.getReplyAction()).queue();
    }

    @Test
    void testSlashEventOutputAdapter_EditNoMusic()
    {
        // Given
        when(fixture.getAudioHandler().getNoMusicPlaying(fixture.getJda())).thenReturn(messageCreateData);
        when(fixture.getEvent().reply(any(MessageCreateData.class))).thenReturn(fixture.getReplyAction());
        SlashOutputAdapters.SlashEventOutputAdapter adapter =
                new SlashOutputAdapters.SlashEventOutputAdapter(fixture.getEvent());

        // When
        adapter.editNoMusic(fixture.getAudioHandler());

        // Then
        verify(fixture.getAudioHandler()).getNoMusicPlaying(fixture.getJda());
        verify(fixture.getEvent()).reply(messageCreateData);
        verify(fixture.getReplyAction()).queue();
    }

    @Test
    void testSlashEventOutputAdapter_OnShowHelp()
    {
        // Given
        when(fixture.getReplyAction().setEphemeral(anyBoolean())).thenReturn(fixture.getReplyAction());
        SlashOutputAdapters.SlashEventOutputAdapter adapter =
                new SlashOutputAdapters.SlashEventOutputAdapter(fixture.getEvent());

        // When
        adapter.onShowHelp();

        // Then
        verify(fixture.getEvent()).reply("⚠️ Please include a song title or URL!");
        verify(fixture.getReplyAction()).setEphemeral(true);
        verify(fixture.getReplyAction()).queue();
    }

    // ==================== InteractionHookOutputAdapter Tests ====================

    @Test
    void testInteractionHookOutputAdapter_ReplySuccess()
    {
        // Given
        SlashOutputAdapters.InteractionHookOutputAdapter adapter =
                new SlashOutputAdapters.InteractionHookOutputAdapter(
                        fixture.getHook(), fixture.getJda(), "⚠️");

        // When
        adapter.replySuccess("Success message");

        // Then
        verify(fixture.getHook()).editOriginal("Success message");
        verify(fixture.getEditAction()).queue();
    }

    @Test
    void testInteractionHookOutputAdapter_ReplyError()
    {
        // Given
        SlashOutputAdapters.InteractionHookOutputAdapter adapter =
                new SlashOutputAdapters.InteractionHookOutputAdapter(
                        fixture.getHook(), fixture.getJda(), "⚠️");

        // When
        adapter.replyError("Error message");

        // Then
        verify(fixture.getHook()).editOriginal("Error message");
        verify(fixture.getEditAction()).queue();
    }

    @Test
    void testInteractionHookOutputAdapter_ReplyWarning()
    {
        // Given
        SlashOutputAdapters.InteractionHookOutputAdapter adapter =
                new SlashOutputAdapters.InteractionHookOutputAdapter(
                        fixture.getHook(), fixture.getJda(), "⚠️");

        // When
        adapter.replyWarning("Warning message");

        // Then
        verify(fixture.getHook()).editOriginal("Warning message");
        verify(fixture.getEditAction()).queue();
    }

    @Test
    void testInteractionHookOutputAdapter_EditMessage()
    {
        // Given
        SlashOutputAdapters.InteractionHookOutputAdapter adapter =
                new SlashOutputAdapters.InteractionHookOutputAdapter(
                        fixture.getHook(), fixture.getJda(), "⚠️");

        // When
        adapter.editMessage("Edit message");

        // Then
        verify(fixture.getHook()).editOriginal("Edit message");
        verify(fixture.getEditAction()).queue();
    }

    @Test
    void testInteractionHookOutputAdapter_EditMessageWithCallback()
    {
        // Given
        fixture.withEditQueueCallback();
        SlashOutputAdapters.InteractionHookOutputAdapter adapter =
                new SlashOutputAdapters.InteractionHookOutputAdapter(
                        fixture.getHook(), fixture.getJda(), "⚠️");
        @SuppressWarnings("unchecked")
        Consumer<net.dv8tion.jda.api.entities.Message> callback = mock(Consumer.class);

        // When
        adapter.editMessage("Edit message", callback);

        // Then
        verify(fixture.getHook()).editOriginal("Edit message");
        verify(callback).accept(fixture.getMessage());
    }

    @Test
    void testInteractionHookOutputAdapter_EditNowPlaying()
    {
        // Given
        when(fixture.getAudioHandler().getNowPlaying(fixture.getJda())).thenReturn(messageCreateData);
        when(fixture.getHook().editOriginal(any(MessageEditData.class))).thenReturn(fixture.getEditAction());
        SlashOutputAdapters.InteractionHookOutputAdapter adapter =
                new SlashOutputAdapters.InteractionHookOutputAdapter(
                        fixture.getHook(), fixture.getJda(), "⚠️");

        // When
        adapter.editNowPlaying(fixture.getAudioHandler());

        // Then
        verify(fixture.getAudioHandler()).getNowPlaying(fixture.getJda());
        verify(fixture.getHook()).editOriginal(any(MessageEditData.class));
        verify(fixture.getEditAction()).queue();
    }

    @Test
    void testInteractionHookOutputAdapter_EditNoMusic()
    {
        // Given
        when(fixture.getAudioHandler().getNoMusicPlaying(fixture.getJda())).thenReturn(messageCreateData);
        when(fixture.getHook().editOriginal(any(MessageEditData.class))).thenReturn(fixture.getEditAction());
        SlashOutputAdapters.InteractionHookOutputAdapter adapter =
                new SlashOutputAdapters.InteractionHookOutputAdapter(
                        fixture.getHook(), fixture.getJda(), "⚠️");

        // When
        adapter.editNoMusic(fixture.getAudioHandler());

        // Then
        verify(fixture.getAudioHandler()).getNoMusicPlaying(fixture.getJda());
        verify(fixture.getHook()).editOriginal(any(MessageEditData.class));
        verify(fixture.getEditAction()).queue();
    }

    @Test
    void testInteractionHookOutputAdapter_OnShowHelp()
    {
        // Given
        SlashOutputAdapters.InteractionHookOutputAdapter adapter =
                new SlashOutputAdapters.InteractionHookOutputAdapter(
                        fixture.getHook(), fixture.getJda(), "⚠️");

        // When
        adapter.onShowHelp();

        // Then
        verify(fixture.getHook()).editOriginal("⚠️ Please include a song title or URL!");
        verify(fixture.getEditAction()).queue();
    }
}
