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
package com.jagrosh.jmusicbot.commands.v2;

import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import net.dv8tion.jda.api.Permission;

/**
 * Base class for Admin-level slash commands.
 * Requires MANAGE_SERVER permission or being the bot owner.
 */
public abstract class AdminSlashCommand extends SlashCommand
{
    protected final Bot bot;

    public AdminSlashCommand(Bot bot)
    {
        this.bot = bot;
        this.guildOnly = true;
        this.category = new Category("Admin");
    }

    @Override
    protected void execute(SlashCommandEvent event)
    {
        // Check if user is owner or has MANAGE_SERVER permission
        boolean isOwner = event.getUser().getId().equals(event.getClient().getOwnerId());
        boolean hasPermission = event.getMember() != null && event.getMember().hasPermission(Permission.MANAGE_SERVER);

        if (!isOwner && !hasPermission)
        {
            event.reply(event.getClient().getError() + " You need the Manage Server permission to use this command!")
                    .setEphemeral(true).queue();
            return;
        }

        doAdminCommand(event);
    }

    /**
     * Override this method to implement the admin command logic.
     */
    public abstract void doAdminCommand(SlashCommandEvent event);
}
