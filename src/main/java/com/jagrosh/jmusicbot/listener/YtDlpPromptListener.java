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

import java.util.Optional;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.ytdlp.MatchSuggestions;
import com.jagrosh.jmusicbot.ytdlp.YtDlpPrompt;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * Routes yt-dlp prompt button presses ({@code ytdlp_one_*}, {@code ytdlp_all_*}) to {@link YtDlpPrompt},
 * and similar-song suggestion presses ({@code ytdlp_match_*}) to {@link MatchSuggestions}.
 *
 * @author Arif Banai (arif-banai)
 */
public class YtDlpPromptListener extends ListenerAdapter {

    private final Bot bot;

    public YtDlpPromptListener(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("ytdlp_")) {
            return;
        }
        if (event.getComponentId().startsWith(ComponentIdParsers.YTDLP_MATCH_PREFIX)) {
            onMatchButton(event);
            return;
        }
        Optional<ComponentIdParsers.YtDlpButtonId> parsed = ComponentIdParsers.parseYtDlpButtonId(event.getComponentId());
        if (parsed.isEmpty()) {
            event.reply("Invalid button state.").setEphemeral(true).queue();
            return;
        }
        YtDlpService ytDlp = bot.getYtDlp();
        YtDlpPrompt prompt = ytDlp == null ? null : ytDlp.getPrompt();
        if (prompt == null) {
            event.reply("This prompt has expired.").setEphemeral(true).queue();
            return;
        }
        prompt.handlePress(event, parsed.get());
    }

    private void onMatchButton(ButtonInteractionEvent event) {
        Optional<ComponentIdParsers.YtDlpMatchButtonId> parsed =
                ComponentIdParsers.parseYtDlpMatchButtonId(event.getComponentId());
        YtDlpService ytDlp = bot.getYtDlp();
        MatchSuggestions suggestions = ytDlp == null ? null : ytDlp.getMatchSuggestions();
        if (parsed.isEmpty() || suggestions == null) {
            event.reply("This suggestion has expired.").setEphemeral(true).queue();
            return;
        }
        suggestions.handlePress(event, parsed.get());
    }
}
