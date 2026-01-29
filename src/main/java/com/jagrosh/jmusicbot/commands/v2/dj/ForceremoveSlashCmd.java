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
import com.jagrosh.jmusicbot.utils.FormatUtil;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;

/**
 * DJ slash command to remove all entries by a user from the queue.
 */
public class ForceremoveSlashCmd extends DJSlashCommand
{
    private final MusicService musicService;

    public ForceremoveSlashCmd(Bot bot)
    {
        super(bot);
        this.musicService = bot.getMusicService();
        this.name = "forceremove";
        this.help = "removes all entries by a user from the queue";
        this.options = Collections.singletonList(
                new OptionData(OptionType.USER, "user", "the user whose entries to remove", true)
        );
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
    }

    @Override
    public void doDJCommand(SlashCommandEvent event)
    {
        if (musicService.isQueueEmpty(event.getGuild()))
        {
            event.reply(event.getClient().getError() + " There is nothing in the queue!")
                    .setEphemeral(true).queue();
            return;
        }

        User target = event.getOption("user").getAsUser();
        int count = musicService.removeAllTracksByUser(event.getGuild(), target.getIdLong());

        if (count == 0)
        {
            event.reply(event.getClient().getWarning() + " **" + target.getName() + "** doesn't have any songs in the queue!")
                    .setEphemeral(true).queue();
        }
        else
        {
            event.reply(event.getClient().getSuccess() + " Successfully removed `" + count + "` entries from " + FormatUtil.formatUsername(target) + ".")
                    .queue();
        }
    }
}
