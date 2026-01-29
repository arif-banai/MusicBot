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
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;

/**
 * Admin slash command to set the DJ role.
 */
public class SetdjSlashCmd extends AdminSlashCommand
{
    public SetdjSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "setdj";
        this.help = "sets the DJ role for certain music commands";
        this.options = Collections.singletonList(
                new OptionData(OptionType.ROLE, "role", "the DJ role (leave empty to clear)", false)
        );
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    public void doAdminCommand(SlashCommandEvent event)
    {
        Settings settings = event.getClient().getSettingsFor(event.getGuild());

        if (event.getOption("role") == null)
        {
            settings.setDJRole(null);
            event.reply(event.getClient().getSuccess() + " DJ role cleared; Only Admins can use the DJ commands.").queue();
        }
        else
        {
            Role role = event.getOption("role").getAsRole();
            settings.setDJRole(role);
            event.reply(event.getClient().getSuccess() + " DJ commands can now be used by users with the **" + role.getName() + "** role.").queue();
        }
    }
}
