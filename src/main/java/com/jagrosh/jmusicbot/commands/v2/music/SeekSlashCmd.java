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
package com.jagrosh.jmusicbot.commands.v2.music;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.MusicSlashCommand;
import com.jagrosh.jmusicbot.commands.v2.SlashOutputAdapters.SlashEventOutputAdapter;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;

/**
 * Slash command to seek to a position in the current track.
 */
public class SeekSlashCmd extends MusicSlashCommand
{
    private final MusicService musicService;

    public SeekSlashCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "seek";
        this.help = "seeks the current song";
        this.options = Collections.singletonList(
                new OptionData(OptionType.STRING, "time", "[+ | -] <HH:MM:SS | MM:SS | SS> or <0h0m0s>", true)
        );
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = true;
    }

    @Override
    public void doCommand(SlashCommandEvent event)
    {
        String timeString = event.getOption("time").getAsString();
        musicService.seek(event.getGuild(), event.getMember(), timeString, new SlashEventOutputAdapter(event));
    }
}
