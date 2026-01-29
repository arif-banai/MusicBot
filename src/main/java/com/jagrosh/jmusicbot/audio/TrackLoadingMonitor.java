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

import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitors track loading operations to track performance and success rates by source.
 * This helps diagnose issues with specific audio sources (YouTube, Spotify, etc.).
 *
 * <p>Tracks for each load request:
 * <ul>
 *   <li>Load duration (time from loadItem call to result)</li>
 *   <li>Source manager (which audio source handled the request)</li>
 *   <li>Load result (success, failure, no match)</li>
 *   <li>Error messages for failures</li>
 * </ul>
 *
 * @author Arif Banai (arif-banai)
 */
public final class TrackLoadingMonitor {
    
    private static final Logger LOG = LoggerFactory.getLogger(TrackLoadingMonitor.class);
    private static final int MAX_LOAD_EVENTS = 500;
    private static final int MAX_EVENTS_PER_SOURCE = 100;
    
    private static volatile TrackLoadingMonitor instance;
    
    // Global load events
    private final ConcurrentLinkedDeque<LoadEvent> loadEvents = new ConcurrentLinkedDeque<>();
    
    // Per-source statistics
    private final ConcurrentMap<String, SourceStats> sourceStats = new ConcurrentHashMap<>();
    
    // Global counters
    private final AtomicLong totalLoads = new AtomicLong(0);
    private final AtomicLong successfulLoads = new AtomicLong(0);
    private final AtomicLong failedLoads = new AtomicLong(0);
    private final AtomicLong noMatchLoads = new AtomicLong(0);
    
    private TrackLoadingMonitor() {
        // Private constructor for singleton
    }
    
    /**
     * Gets the singleton instance.
     */
    public static TrackLoadingMonitor getInstance() {
        if (instance == null) {
            synchronized (TrackLoadingMonitor.class) {
                if (instance == null) {
                    instance = new TrackLoadingMonitor();
                }
            }
        }
        return instance;
    }
    
    /**
     * Wraps an AudioLoadResultHandler to monitor loading performance.
     *
     * @param query the search query or URL
     * @param delegate the original handler to delegate to
     * @return a wrapped handler that records metrics
     */
    public static AudioLoadResultHandler wrap(String query, AudioLoadResultHandler delegate) {
        return getInstance().createWrapper(query, delegate);
    }
    
    private AudioLoadResultHandler createWrapper(String query, AudioLoadResultHandler delegate) {
        long startTime = System.currentTimeMillis();
        
        return new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                long duration = System.currentTimeMillis() - startTime;
                String source = track.getSourceManager() != null 
                    ? track.getSourceManager().getSourceName() 
                    : "unknown";
                
                recordLoad(new LoadEvent(
                    System.currentTimeMillis(),
                    query,
                    source,
                    LoadResult.TRACK_LOADED,
                    duration,
                    track.getInfo().title,
                    null
                ));
                
                delegate.trackLoaded(track);
            }
            
            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                long duration = System.currentTimeMillis() - startTime;
                String source = "unknown";
                if (!playlist.getTracks().isEmpty() && playlist.getTracks().get(0).getSourceManager() != null) {
                    source = playlist.getTracks().get(0).getSourceManager().getSourceName();
                }
                
                recordLoad(new LoadEvent(
                    System.currentTimeMillis(),
                    query,
                    source,
                    LoadResult.PLAYLIST_LOADED,
                    duration,
                    playlist.getName(),
                    null
                ));
                
                delegate.playlistLoaded(playlist);
            }
            
            @Override
            public void noMatches() {
                long duration = System.currentTimeMillis() - startTime;
                String source = guessSourceFromQuery(query);
                
                recordLoad(new LoadEvent(
                    System.currentTimeMillis(),
                    query,
                    source,
                    LoadResult.NO_MATCHES,
                    duration,
                    null,
                    null
                ));
                
                delegate.noMatches();
            }
            
            @Override
            public void loadFailed(FriendlyException exception) {
                long duration = System.currentTimeMillis() - startTime;
                String source = guessSourceFromQuery(query);
                
                recordLoad(new LoadEvent(
                    System.currentTimeMillis(),
                    query,
                    source,
                    LoadResult.LOAD_FAILED,
                    duration,
                    null,
                    exception.getMessage()
                ));
                
                delegate.loadFailed(exception);
            }
        };
    }
    
    /**
     * Guesses the source from the query URL.
     */
    private String guessSourceFromQuery(String query) {
        if (query == null) return "unknown";
        query = query.toLowerCase();
        
        if (query.contains("youtube.com") || query.contains("youtu.be")) {
            return "youtube";
        } else if (query.contains("soundcloud.com")) {
            return "soundcloud";
        } else if (query.contains("spotify.com")) {
            return "spotify";
        } else if (query.contains("twitch.tv")) {
            return "twitch";
        } else if (query.contains("bandcamp.com")) {
            return "bandcamp";
        } else if (query.contains("vimeo.com")) {
            return "vimeo";
        } else if (query.startsWith("http://") || query.startsWith("https://")) {
            return "http";
        }
        return "search";
    }
    
    /**
     * Records a load event.
     */
    private void recordLoad(LoadEvent event) {
        totalLoads.incrementAndGet();
        
        switch (event.result()) {
            case TRACK_LOADED, PLAYLIST_LOADED -> successfulLoads.incrementAndGet();
            case LOAD_FAILED -> failedLoads.incrementAndGet();
            case NO_MATCHES -> noMatchLoads.incrementAndGet();
        }
        
        // Add to global events
        loadEvents.addLast(event);
        while (loadEvents.size() > MAX_LOAD_EVENTS) {
            loadEvents.pollFirst();
        }
        
        // Update source stats
        sourceStats.computeIfAbsent(event.source(), k -> new SourceStats())
            .recordLoad(event);
        
        if (event.result() == LoadResult.LOAD_FAILED) {
            LOG.debug("Track load failed for '{}' from {}: {}", 
                event.query(), event.source(), event.errorMessage());
        }
    }
    
    /**
     * Gets recent load events.
     *
     * @param limit maximum number of events to return
     * @return array of recent load events
     */
    public LoadEvent[] getRecentEvents(int limit) {
        return loadEvents.stream()
            .skip(Math.max(0, loadEvents.size() - limit))
            .toArray(LoadEvent[]::new);
    }
    
    /**
     * Gets load events for a specific source.
     *
     * @param source the source name
     * @param limit maximum number of events to return
     * @return array of load events for the source
     */
    public LoadEvent[] getEventsForSource(String source, int limit) {
        return loadEvents.stream()
            .filter(e -> source.equalsIgnoreCase(e.source()))
            .skip(Math.max(0, loadEvents.size() - limit))
            .limit(limit)
            .toArray(LoadEvent[]::new);
    }
    
    /**
     * Gets statistics for a specific source.
     */
    public SourceStats getSourceStats(String source) {
        return sourceStats.get(source);
    }
    
    /**
     * Gets all source names that have been tracked.
     */
    public String[] getTrackedSources() {
        return sourceStats.keySet().toArray(new String[0]);
    }
    
    /**
     * Gets a snapshot of loading metrics.
     */
    public LoadingSnapshot getSnapshot(int windowSeconds) {
        long cutoff = System.currentTimeMillis() - (windowSeconds * 1000L);
        
        LoadEvent[] recentEvents = loadEvents.stream()
            .filter(e -> e.timestamp() >= cutoff)
            .toArray(LoadEvent[]::new);
        
        int success = 0, failed = 0, noMatch = 0;
        long totalDuration = 0;
        
        for (LoadEvent e : recentEvents) {
            totalDuration += e.loadDurationMs();
            switch (e.result()) {
                case TRACK_LOADED, PLAYLIST_LOADED -> success++;
                case LOAD_FAILED -> failed++;
                case NO_MATCHES -> noMatch++;
            }
        }
        
        double avgDuration = recentEvents.length > 0 ? (double) totalDuration / recentEvents.length : 0;
        double successRate = recentEvents.length > 0 ? (success * 100.0) / recentEvents.length : 100;
        
        // Calculate p95 load time
        double p95Duration = 0;
        if (recentEvents.length >= 5) {
            long[] durations = Arrays.stream(recentEvents)
                .mapToLong(LoadEvent::loadDurationMs)
                .sorted()
                .toArray();
            int p95Index = (int) Math.ceil(durations.length * 0.95) - 1;
            p95Index = Math.max(0, Math.min(p95Index, durations.length - 1));
            p95Duration = durations[p95Index];
        }
        
        return new LoadingSnapshot(
            windowSeconds,
            totalLoads.get(),
            successfulLoads.get(),
            failedLoads.get(),
            noMatchLoads.get(),
            recentEvents.length,
            success,
            failed,
            noMatch,
            avgDuration,
            p95Duration,
            successRate,
            recentEvents,
            sourceStats.keySet().toArray(new String[0])
        );
    }
    
    /**
     * Clears all recorded events and statistics.
     */
    public void clear() {
        loadEvents.clear();
        sourceStats.clear();
        totalLoads.set(0);
        successfulLoads.set(0);
        failedLoads.set(0);
        noMatchLoads.set(0);
    }
    
    /**
     * Load result types.
     */
    public enum LoadResult {
        TRACK_LOADED,
        PLAYLIST_LOADED,
        NO_MATCHES,
        LOAD_FAILED
    }
    
    /**
     * Represents a single track load event.
     */
    public record LoadEvent(
        long timestamp,
        String query,
        String source,
        LoadResult result,
        long loadDurationMs,
        String trackOrPlaylistName,
        String errorMessage
    ) {
        public boolean isSuccess() {
            return result == LoadResult.TRACK_LOADED || result == LoadResult.PLAYLIST_LOADED;
        }
    }
    
    /**
     * Per-source statistics.
     */
    public static class SourceStats {
        private final AtomicLong totalLoads = new AtomicLong(0);
        private final AtomicLong successfulLoads = new AtomicLong(0);
        private final AtomicLong failedLoads = new AtomicLong(0);
        private final AtomicLong totalDurationMs = new AtomicLong(0);
        private volatile long maxDurationMs = 0;
        private final ConcurrentLinkedDeque<Long> recentDurations = new ConcurrentLinkedDeque<>();
        
        void recordLoad(LoadEvent event) {
            totalLoads.incrementAndGet();
            totalDurationMs.addAndGet(event.loadDurationMs());
            
            if (event.loadDurationMs() > maxDurationMs) {
                maxDurationMs = event.loadDurationMs();
            }
            
            if (event.isSuccess()) {
                successfulLoads.incrementAndGet();
            } else if (event.result() == LoadResult.LOAD_FAILED) {
                failedLoads.incrementAndGet();
            }
            
            recentDurations.addLast(event.loadDurationMs());
            while (recentDurations.size() > MAX_EVENTS_PER_SOURCE) {
                recentDurations.pollFirst();
            }
        }
        
        public long getTotalLoads() { return totalLoads.get(); }
        public long getSuccessfulLoads() { return successfulLoads.get(); }
        public long getFailedLoads() { return failedLoads.get(); }
        
        public double getSuccessRate() {
            long total = totalLoads.get();
            return total > 0 ? (successfulLoads.get() * 100.0) / total : 100;
        }
        
        public double getAverageDurationMs() {
            long total = totalLoads.get();
            return total > 0 ? (double) totalDurationMs.get() / total : 0;
        }
        
        public long getMaxDurationMs() { return maxDurationMs; }
        
        public double getP95DurationMs() {
            Long[] durations = recentDurations.toArray(new Long[0]);
            if (durations.length < 5) return getAverageDurationMs();
            
            Arrays.sort(durations);
            int p95Index = (int) Math.ceil(durations.length * 0.95) - 1;
            p95Index = Math.max(0, Math.min(p95Index, durations.length - 1));
            return durations[p95Index];
        }
    }
    
    /**
     * Snapshot of loading metrics for UI display.
     */
    public record LoadingSnapshot(
        int windowSeconds,
        long totalLoadsAllTime,
        long successfulLoadsAllTime,
        long failedLoadsAllTime,
        long noMatchLoadsAllTime,
        int loadsInWindow,
        int successInWindow,
        int failedInWindow,
        int noMatchInWindow,
        double avgLoadDurationMs,
        double p95LoadDurationMs,
        double successRatePercent,
        LoadEvent[] recentEvents,
        String[] trackedSources
    ) {
        public String formattedAvgDuration() {
            if (avgLoadDurationMs >= 1000) {
                return String.format("%.1fs", avgLoadDurationMs / 1000);
            }
            return String.format("%.0fms", avgLoadDurationMs);
        }
    }
}
