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
package com.jagrosh.jmusicbot.commands.v2.dj;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.DJSlashCommand;
import com.jagrosh.jmusicbot.commands.v2.SlashOutputAdapters.InteractionHookOutputAdapter;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;

/**
 * DJ slash command to play a single song next in the queue.
 */
public class PlaynextSlashCmd extends DJSlashCommand
{
    private final MusicService musicService;
    private final String loadingEmoji;

    public PlaynextSlashCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.loadingEmoji = bot.getConfig().getLoading();
        this.name = "playnext";
        this.help = "plays a single song next";
        this.options = Collections.singletonList(
                new OptionData(OptionType.STRING, "query", "song title or URL", true)
        );
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = false;
    }

    @Override
    public void doDJCommand(SlashCommandEvent event)
    {
        String query = event.getOption("query").getAsString();

        event.reply(loadingEmoji + " Loading... `[" + query + "]`").queue(hook ->
        {
            musicService.playNext(event.getGuild(), event.getMember(), query, event.getTextChannel(),
                    new InteractionHookOutputAdapter(hook, event.getJDA(), event.getClient().getWarning()));
        });
    }
}
