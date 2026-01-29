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
package com.jagrosh.jmusicbot.audio;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;

/**
 * Monitors voice connection events to track reconnects and connection state changes.
 * This helps diagnose voice instability issues that can cause audio stuttering.
 *
 * <p>Tracks:
 * <ul>
 *   <li>Voice connects and disconnects</li>
 *   <li>Channel moves (reconnects)</li>
 *   <li>Connection durations</li>
 * </ul>
 *
 * @author Arif Banai (arif-banai)
 */
public final class VoiceConnectionMonitor extends ListenerAdapter {
    
    private static final Logger LOG = LoggerFactory.getLogger(VoiceConnectionMonitor.class);
    private static final int MAX_EVENTS_PER_GUILD = 100;
    private static final int MAX_GLOBAL_EVENTS = 500;
    
    private static volatile VoiceConnectionMonitor instance;
    
    // Per-guild event tracking
    private final ConcurrentMap<Long, ConcurrentLinkedDeque<VoiceEvent>> guildEvents = new ConcurrentHashMap<>();
    
    // Global event tracking (all guilds)
    private final ConcurrentLinkedDeque<VoiceEvent> globalEvents = new ConcurrentLinkedDeque<>();
    
    // Track current connection state per guild
    private final ConcurrentMap<Long, ConnectionState> connectionStates = new ConcurrentHashMap<>();
    
    private volatile JDA jda;
    
    private VoiceConnectionMonitor() {
        // Private constructor for singleton
    }
    
    /**
     * Gets the singleton instance.
     */
    public static VoiceConnectionMonitor getInstance() {
        if (instance == null) {
            synchronized (VoiceConnectionMonitor.class) {
                if (instance == null) {
                    instance = new VoiceConnectionMonitor();
                }
            }
        }
        return instance;
    }
    
    /**
     * Registers this monitor with JDA.
     *
     * @param jda the JDA instance
     */
    public void register(JDA jda) {
        this.jda = jda;
        jda.addEventListener(this);
        LOG.info("Voice Connection Monitor registered");
    }
    
    /**
     * Unregisters this monitor from JDA.
     */
    public void unregister() {
        if (jda != null) {
            jda.removeEventListener(this);
            LOG.info("Voice Connection Monitor unregistered");
        }
    }
    
    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        // Only track bot's own voice events
        if (!event.getMember().getUser().equals(event.getJDA().getSelfUser())) {
            return;
        }
        
        long guildId = event.getGuild().getIdLong();
        long now = System.currentTimeMillis();
        
        if (event.getChannelLeft() == null && event.getChannelJoined() != null) {
            // Connected to voice
            recordEvent(guildId, new VoiceEvent(
                now,
                guildId,
                VoiceEventType.CONNECTED,
                null,
                event.getChannelJoined().getName()
            ));
            
            connectionStates.put(guildId, new ConnectionState(
                now,
                event.getChannelJoined().getName(),
                true,
                0
            ));
            
        } else if (event.getChannelLeft() != null && event.getChannelJoined() == null) {
            // Disconnected from voice
            ConnectionState prevState = connectionStates.get(guildId);
            
            recordEvent(guildId, new VoiceEvent(
                now,
                guildId,
                VoiceEventType.DISCONNECTED,
                event.getChannelLeft().getName(),
                null
            ));
            
            if (prevState != null) {
                connectionStates.put(guildId, new ConnectionState(
                    prevState.connectedSince(),
                    null,
                    false,
                    prevState.reconnectCount()
                ));
            }
            
        } else if (event.getChannelLeft() != null && event.getChannelJoined() != null) {
            // Moved channels (potential reconnect)
            ConnectionState prevState = connectionStates.get(guildId);
            int reconnects = prevState != null ? prevState.reconnectCount() + 1 : 1;
            
            recordEvent(guildId, new VoiceEvent(
                now,
                guildId,
                VoiceEventType.CHANNEL_MOVE,
                event.getChannelLeft().getName(),
                event.getChannelJoined().getName()
            ));
            
            connectionStates.put(guildId, new ConnectionState(
                prevState != null ? prevState.connectedSince() : now,
                event.getChannelJoined().getName(),
                true,
                reconnects
            ));
            
            LOG.debug("Voice channel move in guild {} (reconnect #{}): {} -> {}",
                guildId, reconnects, event.getChannelLeft().getName(), event.getChannelJoined().getName());
        }
    }
    
    /**
     * Records a reconnect event manually (for when we detect reconnects through other means).
     */
    public void recordReconnect(long guildId, String channelName) {
        long now = System.currentTimeMillis();
        
        recordEvent(guildId, new VoiceEvent(
            now,
            guildId,
            VoiceEventType.RECONNECT,
            channelName,
            channelName
        ));
        
        ConnectionState prevState = connectionStates.get(guildId);
        int reconnects = prevState != null ? prevState.reconnectCount() + 1 : 1;
        
        connectionStates.put(guildId, new ConnectionState(
            prevState != null ? prevState.connectedSince() : now,
            channelName,
            true,
            reconnects
        ));
        
        LOG.debug("Voice reconnect recorded in guild {} (reconnect #{})", guildId, reconnects);
    }
    
    /**
     * Records a voice event.
     */
    private void recordEvent(long guildId, VoiceEvent event) {
        // Add to guild-specific queue
        guildEvents.computeIfAbsent(guildId, k -> new ConcurrentLinkedDeque<>())
            .addLast(event);
        
        // Trim guild queue
        ConcurrentLinkedDeque<VoiceEvent> guildQueue = guildEvents.get(guildId);
        while (guildQueue.size() > MAX_EVENTS_PER_GUILD) {
            guildQueue.pollFirst();
        }
        
        // Add to global queue
        globalEvents.addLast(event);
        while (globalEvents.size() > MAX_GLOBAL_EVENTS) {
            globalEvents.pollFirst();
        }
    }
    
    /**
     * Gets recent voice events for a specific guild.
     *
     * @param guildId the guild ID
     * @param windowSeconds how many seconds of history to return
     * @return array of voice events
     */
    public VoiceEvent[] getRecentEvents(long guildId, int windowSeconds) {
        ConcurrentLinkedDeque<VoiceEvent> queue = guildEvents.get(guildId);
        if (queue == null) {
            return new VoiceEvent[0];
        }
        
        long cutoff = System.currentTimeMillis() - (windowSeconds * 1000L);
        return queue.stream()
            .filter(e -> e.timestamp() >= cutoff)
            .toArray(VoiceEvent[]::new);
    }
    
    /**
     * Gets all recent voice events across all guilds.
     *
     * @param windowSeconds how many seconds of history to return
     * @return array of voice events
     */
    public VoiceEvent[] getAllRecentEvents(int windowSeconds) {
        long cutoff = System.currentTimeMillis() - (windowSeconds * 1000L);
        return globalEvents.stream()
            .filter(e -> e.timestamp() >= cutoff)
            .toArray(VoiceEvent[]::new);
    }
    
    /**
     * Gets the current connection state for a guild.
     *
     * @param guildId the guild ID
     * @return connection state or null if not tracked
     */
    public ConnectionState getConnectionState(long guildId) {
        return connectionStates.get(guildId);
    }
    
    /**
     * Gets the reconnect count for a guild in the current session.
     *
     * @param guildId the guild ID
     * @return number of reconnects
     */
    public int getReconnectCount(long guildId) {
        ConnectionState state = connectionStates.get(guildId);
        return state != null ? state.reconnectCount() : 0;
    }
    
    /**
     * Gets a snapshot of voice connection metrics for a guild.
     */
    public VoiceSnapshot getSnapshot(long guildId, int windowSeconds) {
        VoiceEvent[] events = getRecentEvents(guildId, windowSeconds);
        ConnectionState state = getConnectionState(guildId);
        
        int connects = 0, disconnects = 0, moves = 0, reconnects = 0;
        for (VoiceEvent e : events) {
            switch (e.type()) {
                case CONNECTED -> connects++;
                case DISCONNECTED -> disconnects++;
                case CHANNEL_MOVE -> moves++;
                case RECONNECT -> reconnects++;
            }
        }
        
        long connectionDuration = 0;
        if (state != null && state.connected()) {
            connectionDuration = System.currentTimeMillis() - state.connectedSince();
        }
        
        // Get gateway ping if JDA is available
        long gatewayPing = jda != null ? jda.getGatewayPing() : -1;
        
        return new VoiceSnapshot(
            guildId,
            windowSeconds,
            events,
            connects,
            disconnects,
            moves,
            reconnects,
            state != null ? state.reconnectCount() : 0,
            state != null && state.connected(),
            state != null ? state.currentChannel() : null,
            connectionDuration,
            gatewayPing
        );
    }
    
    /**
     * Clears all recorded events.
     */
    public void clear() {
        guildEvents.clear();
        globalEvents.clear();
        connectionStates.clear();
    }
    
    /**
     * Clears events for a specific guild.
     */
    public void clearGuild(long guildId) {
        guildEvents.remove(guildId);
        connectionStates.remove(guildId);
    }
    
    /**
     * Types of voice connection events.
     */
    public enum VoiceEventType {
        CONNECTED,
        DISCONNECTED,
        CHANNEL_MOVE,
        RECONNECT
    }
    
    /**
     * Represents a voice connection event.
     */
    public record VoiceEvent(
        long timestamp,
        long guildId,
        VoiceEventType type,
        String fromChannel,
        String toChannel
    ) {}
    
    /**
     * Represents the current voice connection state for a guild.
     */
    public record ConnectionState(
        long connectedSince,
        String currentChannel,
        boolean connected,
        int reconnectCount
    ) {}
    
    /**
     * Snapshot of voice connection metrics for UI display.
     */
    public record VoiceSnapshot(
        long guildId,
        int windowSeconds,
        VoiceEvent[] events,
        int connectCount,
        int disconnectCount,
        int channelMoveCount,
        int reconnectCountInWindow,
        int totalReconnects,
        boolean currentlyConnected,
        String currentChannel,
        long connectionDurationMs,
        long gatewayPingMs
    ) {
        public String formattedDuration() {
            if (!currentlyConnected) return "Not connected";
            long seconds = connectionDurationMs / 1000;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            if (minutes >= 60) {
                long hours = minutes / 60;
                minutes = minutes % 60;
                return String.format("%d:%02d:%02d", hours, minutes, seconds);
            }
            return String.format("%d:%02d", minutes, seconds);
        }
        
        public boolean hasReconnects() {
            return totalReconnects > 0;
        }
    }
}
