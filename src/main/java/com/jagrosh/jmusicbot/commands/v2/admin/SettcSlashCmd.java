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
package com.jagrosh.jmusicbot.commands.v2.admin;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.AdminSlashCommand;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;

/**
 * Admin slash command to set the text channel for music commands.
 */
public class SettcSlashCmd extends AdminSlashCommand
{
    public SettcSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "settc";
        this.help = "sets the text channel for music commands";
        this.options = Collections.singletonList(
                new OptionData(OptionType.CHANNEL, "channel", "the text channel for music commands (leave empty to clear)", false)
        );
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    public void doAdminCommand(SlashCommandEvent event)
    {
        Settings settings = event.getClient().getSettingsFor(event.getGuild());

        if (event.getOption("channel") == null)
        {
            settings.setTextChannel(null);
            event.reply(event.getClient().getSuccess() + " Music commands can now be used in any channel").queue();
        }
        else
        {
            GuildChannel channel = event.getOption("channel").getAsChannel();
            if (!(channel instanceof TextChannel))
            {
                event.reply(event.getClient().getError() + " Please select a text channel!")
                        .setEphemeral(true).queue();
                return;
            }
            TextChannel textChannel = (TextChannel) channel;
            settings.setTextChannel(textChannel);
            event.reply(event.getClient().getSuccess() + " Music commands can now only be used in " + textChannel.getAsMention()).queue();
        }
    }
}
