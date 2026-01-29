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
 * Admin slash command to set a server-specific prefix.
 */
public class PrefixSlashCmd extends AdminSlashCommand
{
    public PrefixSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "prefix";
        this.help = "sets a server-specific prefix";
        this.options = Collections.singletonList(
                new OptionData(OptionType.STRING, "prefix", "the new prefix (leave empty to clear)", false)
        );
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    public void doAdminCommand(SlashCommandEvent event)
    {
        Settings settings = event.getClient().getSettingsFor(event.getGuild());

        if (event.getOption("prefix") == null)
        {
            settings.setPrefix(null);
            event.reply(event.getClient().getSuccess() + " Prefix cleared.").queue();
        }
        else
        {
            String prefix = event.getOption("prefix").getAsString();
            settings.setPrefix(prefix);
            event.reply(event.getClient().getSuccess() + " Custom prefix set to `" + prefix + "` on *" + event.getGuild().getName() + "*").queue();
        }
    }
}
