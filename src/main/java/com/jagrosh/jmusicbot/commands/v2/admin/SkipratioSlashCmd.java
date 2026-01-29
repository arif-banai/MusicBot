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
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;

/**
 * Admin slash command to set the skip vote percentage.
 */
public class SkipratioSlashCmd extends AdminSlashCommand
{
    public SkipratioSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "setskip";
        this.help = "sets a server-specific skip percentage";
        this.options = Collections.singletonList(
                new OptionData(OptionType.INTEGER, "percentage", "skip percentage (0-100)", true)
                        .setMinValue(0)
                        .setMaxValue(100)
        );
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    public void doAdminCommand(SlashCommandEvent event)
    {
        int value = (int) event.getOption("percentage").getAsLong();

        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        settings.setSkipRatio(value / 100.0);

        event.reply(event.getClient().getSuccess() + " Skip percentage has been set to `" + value + "%` of listeners on *" + event.getGuild().getName() + "*").queue();
    }
}
