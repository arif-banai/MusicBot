/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.commands.v1;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.MusicCommandValidator;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.AudioChannel;
import net.dv8tion.jda.api.exceptions.PermissionException;

/**
 *
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public abstract class MusicCommand extends Command 
{
    protected final Bot bot;
    protected boolean bePlaying;
    protected boolean beListening;
    
    public MusicCommand(Bot bot)
    {
        this.bot = bot;
        this.guildOnly = true;
        this.category = new Category("Music");
    }
    
    @Override
    protected void execute(CommandEvent event)
    {
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        String errorEmoji = event.getClient().getError();

        boolean valid = MusicCommandValidator.validate(
                event.getGuild(),
                event.getMember(),
                event.getTextChannel(),
                settings,
                bot,
                event.getJDA(),
                bePlaying,
                beListening,
                new MusicCommandValidator.ErrorHandler()
                {
                    @Override
                    public void onTextChannelError(TextChannel requiredChannel)
                    {
                        // Text commands: delete message and reply in DM
                        try
                        {
                            event.getMessage().delete().queue();
                        }
                        catch (PermissionException ignore) {}
                        event.replyInDm(errorEmoji + " You can only use that command in " + requiredChannel.getAsMention() + "!");
                    }

                    @Override
                    public void onNotPlayingError()
                    {
                        event.reply(errorEmoji + " There must be music playing to use that!");
                    }

                    @Override
                    public void onNotListeningError(AudioChannel requiredChannel)
                    {
                        String channelName = requiredChannel == null ? "a voice channel" : requiredChannel.getAsMention();
                        event.replyError("You must be listening in " + channelName + " to use that!");
                    }

                    @Override
                    public void onAfkChannelError()
                    {
                        event.replyError("You cannot use that command in an AFK channel!");
                    }

                    @Override
                    public void onVoiceConnectError(AudioChannel channel)
                    {
                        event.reply(errorEmoji + " I am unable to connect to " + channel.getAsMention() + "!");
                    }
                }
        );

        if (valid)
        {
            doCommand(event);
        }
    }
    
    public abstract void doCommand(CommandEvent event);
}
