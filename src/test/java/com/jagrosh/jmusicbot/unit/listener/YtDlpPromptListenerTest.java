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
import com.jagrosh.jmusicbot.listener.YtDlpPromptListener;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.ytdlp.MatchSuggestions;
import com.jagrosh.jmusicbot.ytdlp.YtDlpPrompt;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Routing of yt-dlp buttons: prompt buttons go to the prompt, similar-song buttons to the
 * suggestions, and each press is answered exactly once.
 */
class YtDlpPromptListenerTest {

    private Bot bot;
    private YtDlpService service;
    private YtDlpPrompt prompt;
    private MatchSuggestions suggestions;

    @BeforeEach
    void setUp() {
        bot = mock(Bot.class);
        service = mock(YtDlpService.class);
        prompt = mock(YtDlpPrompt.class);
        suggestions = mock(MatchSuggestions.class);
        when(bot.getYtDlp()).thenReturn(service);
        when(service.getPrompt()).thenReturn(prompt);
        when(service.getMatchSuggestions()).thenReturn(suggestions);
    }

    private static ButtonInteractionEvent press(String componentId) {
        ButtonInteractionEvent event = mock(ButtonInteractionEvent.class);
        when(event.getComponentId()).thenReturn(componentId);
        when(event.reply(anyString())).thenReturn(mock(ReplyCallbackAction.class, RETURNS_SELF));
        return event;
    }

    @Test
    void matchButton_goesToSuggestions_notAnsweredAsInvalid() {
        ButtonInteractionEvent event = press("ytdlp_match_switch_42");

        new YtDlpPromptListener(bot).onButtonInteraction(event);

        verify(suggestions).handlePress(event, new ComponentIdParsers.YtDlpMatchButtonId("switch", 42L, 0L));
        verify(prompt, never()).handlePress(any(), any());
        verify(event, never()).reply(anyString());
    }

    @Test
    void matchButton_withoutSuggestions_isExpired() {
        when(service.getMatchSuggestions()).thenReturn(null);
        ButtonInteractionEvent event = press("ytdlp_match_reject_42");

        new YtDlpPromptListener(bot).onButtonInteraction(event);

        verify(event, times(1)).reply(org.mockito.ArgumentMatchers.contains("expired"));
    }

    @Test
    void promptButton_stillGoesToPrompt() {
        ButtonInteractionEvent event = press("ytdlp_one_42_a_b-c_d1234");

        new YtDlpPromptListener(bot).onButtonInteraction(event);

        verify(prompt).handlePress(event, new ComponentIdParsers.YtDlpButtonId("one", 42L, "a_b-c_d1234"));
        verify(suggestions, never()).handlePress(any(), any());
    }
}
