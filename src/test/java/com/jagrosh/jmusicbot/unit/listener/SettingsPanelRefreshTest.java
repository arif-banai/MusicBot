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
package com.jagrosh.jmusicbot.unit.listener;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.listener.SettingsInteractionListener;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Since JDA 6.7.0, an interaction's channel is a detached snapshot when the bot cannot access
 * that channel, and cache-backed calls on it such as retrieveMessageById throw
 * DetachedEntityException synchronously instead of failing the RestAction.
 */
@DisplayName("Settings panel refresh")
class SettingsPanelRefreshTest {

    @Test
    @DisplayName("a detached interaction channel answers through the hook instead of throwing")
    @SuppressWarnings("unchecked")
    void detachedChannelRepliesThroughHook() throws Exception {
        MessageChannel channel = mock(MessageChannel.class);
        when(channel.isDetached()).thenReturn(true);
        when(channel.retrieveMessageById(anyLong()))
            .thenThrow(new IllegalStateException("DetachedEntityException"));

        InteractionHook hook = mock(InteractionHook.class);
        WebhookMessageCreateAction<?> action = mock(WebhookMessageCreateAction.class);
        when(hook.sendMessage(anyString())).thenReturn((WebhookMessageCreateAction) action);
        when(action.setEphemeral(true)).thenReturn((WebhookMessageCreateAction) action);

        refresh(new SettingsInteractionListener(mock(Bot.class)), mock(Guild.class), channel, hook);

        verify(channel, never()).retrieveMessageById(anyLong());
        verify(hook).sendMessage(anyString());
        verify(action).queue();
    }

    private static void refresh(SettingsInteractionListener listener, Guild guild, MessageChannel channel,
                                InteractionHook hook) throws Exception {
        Method m = SettingsInteractionListener.class.getDeclaredMethod("refreshPanelByMessageId",
            Guild.class, MessageChannel.class, long.class, long.class, String.class, InteractionHook.class);
        m.setAccessible(true);
        m.invoke(listener, guild, channel, 1L, 2L, "user", hook);
    }
}
