/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot;

import com.jagrosh.jmusicbot.audio.AudioHandler;

import com.jagrosh.jmusicbot.commands.SlashCommandRegistry;
import com.jagrosh.jmusicbot.entities.UserInteraction.Level;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.jagrosh.jmusicbot.utils.YoutubeOauth2TokenHandler;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.SessionDisconnectEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.CloseCode;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import com.jagrosh.jmusicbot.service.MusicService;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class Listener extends ListenerAdapter
{
    private final Bot bot;
    
    public Listener(Bot bot)
    {
        this.bot = bot;
    }

    @Override
    public void onReady(ReadyEvent event)
    {
        if(event.getJDA().getGuildCache().isEmpty())
        {
            Logger log = LoggerFactory.getLogger("MusicBot");
            String inviteUrl = event.getJDA().getInviteUrl(JMusicBot.RECOMMENDED_PERMS);
            log.warn("This bot is not on any guilds! Use the following link to add the bot to your guilds!");
            log.warn(inviteUrl);
            bot.getUserInteraction().alert(Level.WARNING, "Setup",
                    "This bot is not on any guilds!\n\nUse this link to add the bot to your server:\n" + inviteUrl);
        }
        
        // Register slash commands if they have changed
        if(bot.getCommandClient() != null)
        {
            SlashCommandRegistry.registerIfChanged(event.getJDA(), bot.getCommandClient());
        }
        
        credit(event.getJDA());
        event.getJDA().getGuilds().forEach((Guild guild) ->
        {
            try
            {
                String defpl = bot.getSettingsManager().getSettings(guild).getDefaultPlaylist();
                VoiceChannel vc = bot.getSettingsManager().getSettings(guild).getVoiceChannel(guild);
                if(defpl!=null && vc!=null && bot.getPlayerManager().setUpHandler(guild).playFromDefault())
                {
                    guild.getAudioManager().openAudioConnection(vc);
                }
            }
            catch(Exception ignore) {}
        });
        if(bot.getConfig().useUpdateAlerts())
        {
            bot.getThreadpool().scheduleWithFixedDelay(() -> 
            {
                try
                {
                    User owner = bot.getJDA().retrieveUserById(bot.getConfig().getOwnerId()).complete();
                    String currentVersion = OtherUtil.getCurrentVersion();
                    String latestVersion = OtherUtil.getLatestVersion();
                    if(latestVersion != null && OtherUtil.isNewerVersion(currentVersion, latestVersion))
                    {
                        String msg = String.format(OtherUtil.NEW_VERSION_AVAILABLE, currentVersion, latestVersion);
                        owner.openPrivateChannel().queue(pc -> pc.sendMessage(msg).queue());
                    }
                }
                catch(Exception ignored) {} // ignored
            }, 0, 24, TimeUnit.HOURS);
        }
        if (bot.getConfig().useYouTubeOauth())
        {
            YoutubeOauth2TokenHandler.Data data = bot.getYouTubeOauth2Handler().getData();
            if (data != null)
            {
                try
                {
                    PrivateChannel channel = bot.getJDA().openPrivateChannelById(bot.getConfig().getOwnerId()).complete();
                    channel
                            .sendMessage(
                                    "# DO NOT AUTHORISE THIS WITH YOUR MAIN GOOGLE ACCOUNT!!!\n"
                                            + "## Create or use an alternative/burner Google account!\n"
                                            + "To give JMusicBot access to your Google account, go to "
                                            + data.getAuthorisationUrl()
                                            + " and enter the code **" + data.getCode() + "**")
                            .queue();
                }
                catch (Exception ignored) {}
            }
        }
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event)
    {
        if(event.isFromGuild())
            bot.getNowplayingHandler().onMessageDelete(event.getGuild(), event.getMessageIdLong());
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event)
    {
        if (!event.getComponentId().equals("stop") && !event.getComponentId().equals("pause") && !event.getComponentId().equals("skip")
                && !event.getComponentId().equals("previous") && !event.getComponentId().equals("shuffle")
                && !event.getComponentId().equals("repeat") && !event.getComponentId().equals("voldown")
                && !event.getComponentId().equals("volup"))
            return;

        if (event.getGuild() == null || event.getMember() == null) return;

        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler == null)
        {
            event.reply("There is no music playing!").setEphemeral(true).queue();
            return;
        }

        // Permissions check
        if (!event.getMember().getVoiceState().inAudioChannel() ||
                !event.getMember().getVoiceState().getChannel().equals(event.getGuild().getSelfMember().getVoiceState().getChannel()))
        {
            event.reply("You must be in the same voice channel to use this!").setEphemeral(true).queue();
            return;
        }

        MusicService musicService = bot.getMusicService();
        MusicService.OutputAdapter adapter = new MusicService.OutputAdapter() {
            @Override
            public void replySuccess(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void replyError(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void replyWarning(String content) {
                event.reply(content).setEphemeral(true).queue();
            }

            @Override
            public void editMessage(String content) {
                event.editMessage(content).queue();
            }

            @Override
            public void editMessage(String content, Consumer<net.dv8tion.jda.api.entities.Message> onSuccess) {
                event.editMessage(content).queue(hook -> hook.retrieveOriginal().queue(onSuccess));
            }

            @Override
            public void editNowPlaying(AudioHandler handler) {
                event.editMessage(MessageEditData.fromCreateData(handler.getNowPlaying(event.getJDA()))).queue();
            }

            @Override
            public void editNoMusic(AudioHandler handler) {
                event.editMessage(MessageEditData.fromCreateData(handler.getNoMusicPlaying(event.getJDA()))).queue();
            }

            @Override
            public void onShowHelp() {
                // Not used for buttons
            }
        };

        switch (event.getComponentId())
        {
            case "previous":
                musicService.previous(event.getGuild(), event.getMember(), adapter);
                break;
            case "shuffle":
                musicService.shuffle(event.getGuild(), event.getMember(), 0, adapter);
                break;
            case "repeat":
                musicService.cycleRepeatMode(event.getGuild(), event.getMember(), adapter);
                break;
            case "voldown":
                musicService.adjustVolume(event.getGuild(), event.getMember(), -10, adapter);
                break;
            case "volup":
                musicService.adjustVolume(event.getGuild(), event.getMember(), 10, adapter);
                break;
            case "stop":
                musicService.stop(event.getGuild(), event.getMember(), adapter);
                break;
            case "pause":
                musicService.pause(event.getGuild(), event.getMember(), adapter);
                break;
            case "skip":
                musicService.skip(event.getGuild(), event.getMember(), adapter);
                break;
        }
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event)
    {
        bot.getAloneInVoiceHandler().onVoiceUpdate(event);
    }

    @Override
    public void onSessionDisconnect(@NotNull SessionDisconnectEvent event)
    {
        CloseCode closeCode = event.getCloseCode();
        if (closeCode == CloseCode.DISALLOWED_INTENTS)
        {
            bot.getUserInteraction().alert(
                Level.ERROR,
                "JMusicBot",
                "Your bot is missing required Discord intents!\n\n" +
                "To fix this:\n" +
                "1. Go to https://discord.com/developers/applications\n" +
                "2. Select your bot application\n" +
                "3. Go to 'Bot' settings\n" +
                "4. Enable 'MESSAGE CONTENT INTENT' under Privileged Gateway Intents\n" +
                "5. Save changes and restart JMusicBot"
            );
        }
    }

    @Override
    public void onShutdown(@NotNull ShutdownEvent event)
    {
        bot.shutdown();
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) 
    {
        credit(event.getJDA());
    }
    
    // make sure people aren't adding clones to dbots
    private void credit(JDA jda)
    {
        Guild dbots = jda.getGuildById(110373943822540800L);
        if(dbots==null)
            return;
        if(bot.getConfig().getDBots())
            return;
        jda.getTextChannelById(119222314964353025L)
                .sendMessage("This account is running JMusicBot. Please do not list bot clones on this server, <@"+bot.getConfig().getOwnerId()+">.").complete();
        dbots.leave().queue();
    }
}
