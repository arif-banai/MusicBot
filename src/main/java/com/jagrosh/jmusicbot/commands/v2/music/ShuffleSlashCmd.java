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
import com.jagrosh.jmusicbot.service.MusicService;

/**
 * Slash command to shuffle the user's tracks in the queue.
 */
public class ShuffleSlashCmd extends MusicSlashCommand
{
    private final MusicService musicService;

    public ShuffleSlashCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "shuffle";
        this.help = "shuffles songs you have added";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = true;
    }

    @Override
    public void doCommand(SlashCommandEvent event)
    {
        int shuffled = musicService.shuffleUserTracks(event.getGuild(), event.getUser().getIdLong());
        switch (shuffled)
        {
            case 0:
                event.reply(event.getClient().getError() + " You don't have any music in the queue to shuffle!")
                        .setEphemeral(true).queue();
                break;
            case 1:
                event.reply(event.getClient().getWarning() + " You only have one song in the queue!")
                        .setEphemeral(true).queue();
                break;
            default:
                event.reply(event.getClient().getSuccess() + " You successfully shuffled your " + shuffled + " entries.")
                        .queue();
                break;
        }
    }
}
