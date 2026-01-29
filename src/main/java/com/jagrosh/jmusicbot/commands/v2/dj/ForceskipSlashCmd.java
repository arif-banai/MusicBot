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
import com.jagrosh.jmusicbot.service.MusicService;

/**
 * DJ slash command to force skip the current song.
 */
public class ForceskipSlashCmd extends DJSlashCommand
{
    private final MusicService musicService;

    public ForceskipSlashCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "forceskip";
        this.help = "skips the current song";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
    }

    @Override
    public void doDJCommand(SlashCommandEvent event)
    {
        MusicService.ForceSkipResult result = musicService.forceSkip(event.getGuild());
        if (result != null)
        {
            event.reply(event.getClient().getSuccess() + " Skipped **" + result.trackTitle + "** " + result.requesterInfo).queue();
        }
        else
        {
            event.reply(event.getClient().getWarning() + " Nothing is currently playing!").setEphemeral(true).queue();
        }
    }
}
