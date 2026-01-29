/*
 * Copyright 2016 John Grosh (jagrosh).
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

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jmusicbot.commands.v1.CommandFactory;
import com.jagrosh.jmusicbot.entities.Prompt;
import com.jagrosh.jmusicbot.entities.UserInteraction;
import com.jagrosh.jmusicbot.gui.GUI;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.utils.ConsoleUtil;
import com.jagrosh.jmusicbot.utils.InstanceLock;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 *
 * @author John Grosh (jagrosh)
 */
public class JMusicBot 
{
    public final static Logger LOG = LoggerFactory.getLogger(JMusicBot.class);
    public final static Permission[] RECOMMENDED_PERMS = {
            Permission.VIEW_CHANNEL,
            Permission.MESSAGE_SEND,
            Permission.MESSAGE_HISTORY,
            Permission.MESSAGE_ADD_REACTION,
            Permission.MESSAGE_EMBED_LINKS,
            Permission.MESSAGE_ATTACH_FILES,
            Permission.MESSAGE_MANAGE,
            Permission.MESSAGE_EXT_EMOJI,
            Permission.VOICE_CONNECT,
            Permission.VOICE_SPEAK,
            Permission.NICKNAME_CHANGE
    };
    public final static GatewayIntent[] INTENTS = {
            GatewayIntent.DIRECT_MESSAGES,
            GatewayIntent.GUILD_MESSAGES,
            GatewayIntent.GUILD_MESSAGE_REACTIONS,
            GatewayIntent.GUILD_VOICE_STATES,
            GatewayIntent.MESSAGE_CONTENT
    };
    
    /**
     * @param args the command line arguments
     */
    public static void main(String[] args)
    {
        if(args.length > 0) {
            if (args[0].equalsIgnoreCase("generate-config")) {
                // Use headless prompt for config generation (nogui=true, noprompt=true)
                UserInteraction userInteraction = new Prompt(null, null, true, true);
                BotConfig.writeDefaultConfig(userInteraction);
                return;
            }
        }
        startBot();
    }
    
    private static void startBot()
    {
        // create user interaction handler for startup
        UserInteraction userInteraction = new Prompt("JMusicBot");
        
        // Redirect System.out/err to GUI console early (before config loading)
        // so that all logs, including those from config loading, appear in GUI
        if(!userInteraction.isNoGUI())
        {
            try 
            {
                ConsoleUtil.redirectSystemStreams();
            }
            catch(Exception e)
            {
                LOG.warn("Could not redirect console streams to GUI. Logs may not appear in GUI console.");
            }
        }
        
        // Check for another running instance
        if (!InstanceLock.tryAcquire()) {
            userInteraction.alert(UserInteraction.Level.ERROR, "JMusicBot",
                    "Another instance of JMusicBot is already running.\n" +
                    "Running multiple instances with the same configuration causes duplicate responses to commands.\n" +
                    "Please close the other instance first.");
            System.exit(1);
        }
        
        // startup checks
        OtherUtil.checkVersion(userInteraction);
        OtherUtil.checkJavaVersion(userInteraction);
        
        // load config
        BotConfig config = new BotConfig(userInteraction);
        config.load();
        if(!config.isValid())
            return;
        LOG.info("Loaded config from {}", config.getConfigLocation());

        // set log level from config
        ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(
                ch.qos.logback.classic.Level.toLevel(config.getLogLevel(), ch.qos.logback.classic.Level.INFO));
        
        // set up the listener
        EventWaiter waiter = new EventWaiter();
        SettingsManager settings = new SettingsManager();
        Bot bot = new Bot(waiter, config, settings, userInteraction);
        
        // Initialize GUI (ConsolePanel will reuse the already-redirected streams)
        if(!userInteraction.isNoGUI())
        {
            try 
            {
                GUI gui = new GUI(bot);
                bot.setGUI(gui);
                gui.init();
            }
            catch(Exception e)
            {
                LOG.error("Could not start GUI. Use -Dnogui=true for server environments.");
                userInteraction.alert(UserInteraction.Level.ERROR, "JMusicBot",
                        "Could not start GUI.\nUse -Dnogui=true for server environments.");
            }
        }
        
        CommandClient client = CommandFactory.createCommandClient(config, settings, bot);
        bot.setCommandClient(client);

        // Now that GUI/Logging is ready, initialize the player manager
        bot.getPlayerManager().init();

        // attempt to log in and start
        try
        {
            JDA jda = DiscordService.createJDA(config, bot, waiter, client, userInteraction);
            bot.setJDA(jda);
        }
        catch(IllegalArgumentException ex)
        {
            userInteraction.alert(UserInteraction.Level.ERROR, "JMusicBot",
                    "Invalid configuration. Check your token.\nConfig Location: " + config.getConfigLocation());
            System.exit(1);
        }
        catch(ErrorResponseException ex)
        {
            userInteraction.alert(UserInteraction.Level.ERROR, "JMusicBot", "Invalid response from Discord. Check your internet connection.");
            System.exit(1);
        }
        catch(Exception ex)
        {
            LOG.error("An unexpected error occurred during startup", ex);
            System.exit(1);
        }
    }
}
