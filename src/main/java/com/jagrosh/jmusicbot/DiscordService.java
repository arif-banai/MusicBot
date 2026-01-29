package com.jagrosh.jmusicbot;

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jmusicbot.entities.UserInteraction.Level;
import com.jagrosh.jmusicbot.entities.UserInteraction;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.sedmelluq.discord.lavaplayer.jdaudp.NativeAudioSendFactory;
import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

public class DiscordService {
    private static final Logger LOG = LoggerFactory.getLogger(DiscordService.class);

    public static JDA createJDA(BotConfig config, Bot bot, EventWaiter waiter, CommandClient client, UserInteraction userInteraction) throws Exception {
        JDA jda = JDABuilder.create(config.getToken(), Arrays.asList(JMusicBot.INTENTS))
                .enableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
                .disableCache(
                        CacheFlag.ACTIVITY,
                        CacheFlag.CLIENT_STATUS,
                        CacheFlag.EMOJI,
                        CacheFlag.ONLINE_STATUS,
                        CacheFlag.STICKER,
                        CacheFlag.SCHEDULED_EVENTS
                )
                .setActivity(config.isGameNone() ? null : Activity.playing("loading..."))
                .setStatus(config.getStatus() == OnlineStatus.INVISIBLE || config.getStatus() == OnlineStatus.OFFLINE
                        ? OnlineStatus.INVISIBLE
                        : OnlineStatus.DO_NOT_DISTURB)
                .addEventListeners(client, waiter, new Listener(bot))
                .setBulkDeleteSplittingEnabled(true)
                .setAudioModuleConfig(
                        new AudioModuleConfig()
                                .withDaveSessionFactory(new JDaveSessionFactory())
                                .withAudioSendFactory(new NativeAudioSendFactory())
                )
                .setEnableShutdownHook(true)
                .build();

        // Perform post-startup validation
        String unsupportedReason = OtherUtil.getUnsupportedBotReason(jda);
        if (unsupportedReason != null) {
            userInteraction.alert(Level.ERROR, "JMusicBot", "JMusicBot cannot be run on this Discord bot: " + unsupportedReason);
            jda.shutdown();
            System.exit(1);
        }

        if (!"@mention".equals(config.getPrefix())) {
            LOG.info("You currently have a custom prefix set. If it's not working, ensure 'MESSAGE CONTENT INTENT' is enabled.");
        }

        return jda;
    }
}