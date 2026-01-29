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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitors JVM garbage collection events to help correlate with audio stutters.
 * Runs on a separate daemon thread to avoid impacting audio processing.
 *
 * <p>Uses {@link GarbageCollectorMXBean} to detect GC events by polling
 * collection count and time. When a change is detected, a GC event is recorded.
 *
 * @author Arif Banai (arif-banai)
 */
public final class GCMonitor {
    
    private static final Logger LOG = LoggerFactory.getLogger(GCMonitor.class);
    private static final int MAX_EVENTS = 200;
    private static final int POLL_INTERVAL_MS = 500; // Poll every 500ms
    
    private static volatile GCMonitor instance;
    
    private final ConcurrentLinkedDeque<GCEvent> events = new ConcurrentLinkedDeque<>();
    private final List<GCState> gcStates = new ArrayList<>();
    private final ScheduledExecutorService scheduler;
    private volatile boolean running = false;
    
    private GCMonitor() {
        // Initialize GC state tracking
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            gcStates.add(new GCState(gc.getName(), gc.getCollectionCount(), gc.getCollectionTime()));
        }
        
        // Create daemon scheduler
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "GCMonitor");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Gets the singleton instance.
     */
    public static GCMonitor getInstance() {
        if (instance == null) {
            synchronized (GCMonitor.class) {
                if (instance == null) {
                    instance = new GCMonitor();
                }
            }
        }
        return instance;
    }
    
    /**
     * Starts the GC monitoring thread.
     */
    public void start() {
        if (running) return;
        
        running = true;
        scheduler.scheduleAtFixedRate(this::pollGC, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
        LOG.info("GC Monitor started");
    }
    
    /**
     * Stops the GC monitoring thread.
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        LOG.info("GC Monitor stopped");
    }
    
    /**
     * Polls GC beans and records any new GC events.
     */
    private void pollGC() {
        try {
            List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            long now = System.currentTimeMillis();
            
            for (int i = 0; i < gcBeans.size() && i < gcStates.size(); i++) {
                GarbageCollectorMXBean gc = gcBeans.get(i);
                GCState state = gcStates.get(i);
                
                long currentCount = gc.getCollectionCount();
                long currentTime = gc.getCollectionTime();
                
                if (currentCount > state.lastCount) {
                    // GC occurred since last poll
                    long collections = currentCount - state.lastCount;
                    long timeSpent = currentTime - state.lastTime;
                    
                    // Record the event
                    GCEvent event = new GCEvent(
                        now,
                        gc.getName(),
                        collections,
                        timeSpent
                    );
                    events.addLast(event);
                    
                    // Trim old events
                    while (events.size() > MAX_EVENTS) {
                        events.pollFirst();
                    }
                    
                    // Update state
                    state.lastCount = currentCount;
                    state.lastTime = currentTime;
                    
                    // Log significant GC events
                    if (timeSpent > 50) {
                        LOG.debug("GC detected: {} - {} collections, {}ms", gc.getName(), collections, timeSpent);
                    }
                }
            }
        } catch (Exception e) {
            LOG.error("Error polling GC stats", e);
        }
    }
    
    /**
     * Gets recent GC events within the specified time window.
     *
     * @param windowSeconds how many seconds of history to return
     * @return array of GC events
     */
    public GCEvent[] getRecentEvents(int windowSeconds) {
        long cutoff = System.currentTimeMillis() - (windowSeconds * 1000L);
        
        return events.stream()
            .filter(e -> e.timestamp >= cutoff)
            .toArray(GCEvent[]::new);
    }
    
    /**
     * Gets all recorded GC events.
     */
    public GCEvent[] getAllEvents() {
        return events.toArray(new GCEvent[0]);
    }
    
    /**
     * Clears all recorded events.
     */
    public void clear() {
        events.clear();
    }
    
    /**
     * Gets total GC count across all collectors.
     */
    public long getTotalGCCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionCount)
            .sum();
    }
    
    /**
     * Gets total GC time across all collectors.
     */
    public long getTotalGCTime() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
            .mapToLong(GarbageCollectorMXBean::getCollectionTime)
            .sum();
    }
    
    /**
     * Mutable state for tracking GC bean changes.
     */
    private static class GCState {
        final String name;
        long lastCount;
        long lastTime;
        
        GCState(String name, long count, long time) {
            this.name = name;
            this.lastCount = count;
            this.lastTime = time;
        }
    }
    
    /**
     * Represents a detected GC event.
     */
    public record GCEvent(
        long timestamp,
        String collectorName,
        long collections,
        long durationMs
    ) {
        /**
         * Returns severity based on duration.
         */
        public Severity severity() {
            if (durationMs >= 100) return Severity.SEVERE;
            if (durationMs >= 50) return Severity.MODERATE;
            if (durationMs >= 20) return Severity.MINOR;
            return Severity.MINIMAL;
        }
        
        public enum Severity {
            MINIMAL, MINOR, MODERATE, SEVERE
        }
    }
}
