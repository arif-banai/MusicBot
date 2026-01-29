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
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.v2.AdminSlashCommand;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;

/**
 * Admin slash command to change the queue type.
 */
public class QueuetypeSlashCmd extends AdminSlashCommand
{
    public QueuetypeSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "queuetype";
        this.help = "changes the queue type";
        this.options = Collections.singletonList(
                new OptionData(OptionType.STRING, "type", "queue type", false)
                        .addChoice("linear", "LINEAR")
                        .addChoice("fair", "FAIR")
        );
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    public void doAdminCommand(SlashCommandEvent event)
    {
        Settings settings = event.getClient().getSettingsFor(event.getGuild());

        if (event.getOption("type") == null)
        {
            QueueType currentType = settings.getQueueType();
            event.reply(currentType.getEmoji() + " Current queue type is: `" + currentType.getUserFriendlyName() + "`.").queue();
            return;
        }

        String typeArg = event.getOption("type").getAsString();
        QueueType value;
        try
        {
            value = QueueType.valueOf(typeArg.toUpperCase());
        }
        catch (IllegalArgumentException e)
        {
            event.reply(event.getClient().getError() + " Invalid queue type. Valid types are: linear, fair")
                    .setEphemeral(true).queue();
            return;
        }

        if (settings.getQueueType() != value)
        {
            settings.setQueueType(value);

            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            if (handler != null)
                handler.setQueueType(value);
        }

        event.reply(value.getEmoji() + " Queue type was set to `" + value.getUserFriendlyName() + "`.").queue();
    }
}
