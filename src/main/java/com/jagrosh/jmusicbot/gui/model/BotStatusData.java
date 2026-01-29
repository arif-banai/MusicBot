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
package com.jagrosh.jmusicbot.gui.model;

import com.jagrosh.jmusicbot.Bot;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Immutable record holding bot status data for GUI display.
 * Single source of truth for status information used by StatusBar and StatusPanel.
 *
 * @param connected whether the bot is connected to Discord
 * @param jdaStatus the JDA connection status
 * @param guildCount number of guilds the bot is in
 * @param voiceConnections number of active voice connections
 * @param guilds list of connected guilds (may be empty if disconnected)
 * @param uptimeString formatted uptime string
 *
 * @author Arif Banai (arif-banai)
 */
public record BotStatusData(
    boolean connected,
    JDA.Status jdaStatus,
    int guildCount,
    int voiceConnections,
    List<Guild> guilds,
    String uptimeString
) {
    
    /**
     * Creates a disconnected status with no data.
     */
    public static BotStatusData disconnected() {
        return new BotStatusData(false, null, 0, 0, List.of(), "--");
    }
    
    /**
     * Fetches current status data from the bot.
     *
     * @param bot the bot instance
     * @param startTime the application start time for uptime calculation
     * @return current status data
     */
    public static BotStatusData fromBot(Bot bot, Instant startTime) {
        String uptimeStr = formatUptime(startTime);
        
        if (bot == null) {
            return new BotStatusData(false, null, 0, 0, List.of(), uptimeStr);
        }
        
        JDA jda = bot.getJDA();
        if (jda == null) {
            return new BotStatusData(false, null, 0, 0, List.of(), uptimeStr);
        }
        
        JDA.Status status = jda.getStatus();
        boolean connected = status == JDA.Status.CONNECTED;
        
        if (!connected) {
            return new BotStatusData(false, status, 0, 0, List.of(), uptimeStr);
        }
        
        List<Guild> guilds = jda.getGuilds();
        int guildCount = guilds.size();
        
        // Count active voice connections
        int voiceConnections = (int) guilds.stream()
            .filter(g -> g.getAudioManager().isConnected())
            .count();
        
        return new BotStatusData(true, status, guildCount, voiceConnections, guilds, uptimeStr);
    }
    
    /**
     * Formats uptime duration as a human-readable string.
     */
    private static String formatUptime(Instant startTime) {
        if (startTime == null) {
            return "--";
        }
        
        Duration uptime = Duration.between(startTime, Instant.now());
        long days = uptime.toDays();
        long hours = uptime.toHoursPart();
        long minutes = uptime.toMinutesPart();
        long seconds = uptime.toSecondsPart();
        
        if (days > 0) {
            return String.format("%dd %dh %dm", days, hours, minutes);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else {
            return String.format("%dm %ds", minutes, seconds);
        }
    }
}
