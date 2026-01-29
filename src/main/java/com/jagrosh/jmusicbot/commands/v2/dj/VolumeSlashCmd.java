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
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.v2.DJSlashCommand;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;

/**
 * Slash command to get or set the player volume.
 */
public class VolumeSlashCmd extends DJSlashCommand
{
    public VolumeSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "volume";
        this.help = "sets or shows the player volume";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.options = Collections.singletonList(
                new OptionData(OptionType.INTEGER, "level", "Volume level (0-150)", false)
                        .setMinValue(0)
                        .setMaxValue(150)
        );
    }

    @Override
    public void doDJCommand(SlashCommandEvent event)
    {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        int currentVolume = handler.getPlayer().getVolume();

        if (event.getOption("level") == null)
        {
            // Show current volume
            event.reply(FormatUtil.volumeIcon(currentVolume) + " Current volume is `" + currentVolume + "`").queue();
        }
        else
        {
            int newVolume = (int) event.getOption("level").getAsLong();
            handler.getPlayer().setVolume(newVolume);
            settings.setVolume(newVolume);
            event.reply(FormatUtil.volumeIcon(newVolume) + " Volume changed from `" + currentVolume + "` to `" + newVolume + "`").queue();
        }
    }
}
