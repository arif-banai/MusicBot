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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Collects performance metrics for audio frame processing using lock-free ring buffers.
 * Designed for minimal overhead on the audio thread (O(1) recording, no allocations).
 *
 * <p>JDA requests audio frames every ~20ms. This class tracks:
 * <ul>
 *   <li>Frame availability (provided vs missed - the key stutter indicator)</li>
 *   <li>Frame latency (time to get frame from Lavaplayer)</li>
 *   <li>Consecutive frame streaks (buffer health indicator)</li>
 *   <li>Stutter events (consecutive missed frames)</li>
 * </ul>
 *
 * @author Arif Banai (arif-banai)
 */
public class PerformanceMetrics {
    
    /** Maximum number of frames to retain (2 minutes at 50fps) */
    private static final int MAX_HISTORY_SIZE = 6000;
    
    /** Maximum stutter events to retain */
    private static final int MAX_STUTTER_EVENTS = 100;
    
    /** Maximum stuck events to retain */
    private static final int MAX_STUCK_EVENTS = 50;
    
    /** Maximum track events to retain */
    private static final int MAX_TRACK_EVENTS = 100;
    
    /** Frame counts for miss rate windows (10s = 500 frames, 60s = 3000 frames at 50fps) */
    private static final int FRAMES_10_SECONDS = 500;
    private static final int FRAMES_60_SECONDS = 3000;
    
    private final long guildId;
    
    /** Expected interval between frame requests (Discord requests frames every 20ms) */
    private static final long EXPECTED_CADENCE_MS = 20;
    
    /** Threshold for considering a frame "late" (jitter threshold) */
    private static final long LATE_THRESHOLD_MS = 5;
    
    // Ring buffer for frame data - primitive arrays for zero allocation
    private final long[] timestamps = new long[MAX_HISTORY_SIZE];
    private final boolean[] frameProvided = new boolean[MAX_HISTORY_SIZE];
    private final float[] latencyMs = new float[MAX_HISTORY_SIZE]; // float saves memory, sufficient precision
    private final short[] cadenceDeltaMs = new short[MAX_HISTORY_SIZE]; // delta from expected 20ms (short saves memory)
    private final AtomicInteger ringHead = new AtomicInteger(0);
    private volatile int frameCount = 0; // How many frames recorded (up to MAX_HISTORY_SIZE)
    
    // Stutter events - use ConcurrentLinkedDeque (stutters are rare, allocation OK)
    private final ConcurrentLinkedDeque<StutterEvent> stutterEvents = new ConcurrentLinkedDeque<>();
    
    // Stuck events (Lavaplayer track stuck) - rare, allocation OK
    private final ConcurrentLinkedDeque<StuckEvent> stuckEvents = new ConcurrentLinkedDeque<>();
    
    // Track events (start/end/exception/stuck) - rare, allocation OK
    private final ConcurrentLinkedDeque<TrackEvent> trackEvents = new ConcurrentLinkedDeque<>();
    
    // Time-to-first-frame tracking
    private volatile long trackStartTime = 0;
    private volatile long firstFrameTime = 0;
    private volatile boolean waitingForFirstFrame = false;
    private volatile long lastTimeToFirstFrameMs = 0;
    
    // Counters (atomic for thread safety)
    private final AtomicLong totalFramesRequested = new AtomicLong(0);
    private final AtomicLong totalFramesProvided = new AtomicLong(0);
    private final AtomicLong totalFramesMissed = new AtomicLong(0);
    private final AtomicLong totalLatencyNanos = new AtomicLong(0);
    private final AtomicLong stutterCount = new AtomicLong(0);
    private final AtomicLong stuckCount = new AtomicLong(0);
    
    // Tracking for stutter detection and buffer health
    private volatile int consecutiveMisses = 0;
    private volatile long lastMissTimestamp = 0;
    private volatile int consecutiveSuccess = 0; // For buffer health
    private volatile long sessionStartTime;
    
    // Cadence tracking (time between frame requests)
    private volatile long lastFrameRequestTime = 0;
    private final AtomicLong totalCadenceDeltaMs = new AtomicLong(0);
    private final AtomicLong lateFrameCount = new AtomicLong(0);
    private volatile long maxCadenceDeltaMs = 0;
    
    public PerformanceMetrics(long guildId) {
        this.guildId = guildId;
        this.sessionStartTime = System.currentTimeMillis();
    }
    
    /**
     * Records a frame request and its result.
     * This method is called on the audio thread every ~20ms and must be extremely fast.
     * O(1) time complexity, no allocations in steady state.
     *
     * @param provided true if audio frame was available, false if missed
     * @param provisionLatencyNanos time taken to get frame from Lavaplayer in nanoseconds
     */
    public void recordFrame(boolean provided, long provisionLatencyNanos) {
        long currentTimeMs = System.currentTimeMillis();
        
        // Calculate cadence delta (deviation from expected 20ms interval)
        long cadenceDelta = 0;
        if (lastFrameRequestTime > 0) {
            long actualInterval = currentTimeMs - lastFrameRequestTime;
            cadenceDelta = actualInterval - EXPECTED_CADENCE_MS;
            
            // Track late frames (significant deviation from expected cadence)
            if (cadenceDelta > LATE_THRESHOLD_MS) {
                lateFrameCount.incrementAndGet();
            }
            
            // Track total and max delta for statistics
            if (cadenceDelta > 0) {
                totalCadenceDeltaMs.addAndGet(cadenceDelta);
                if (cadenceDelta > maxCadenceDeltaMs) {
                    maxCadenceDeltaMs = cadenceDelta;
                }
            }
        }
        lastFrameRequestTime = currentTimeMs;
        
        // Update counters (atomic, lock-free)
        totalFramesRequested.incrementAndGet();
        
        if (provided) {
            totalFramesProvided.incrementAndGet();
            totalLatencyNanos.addAndGet(provisionLatencyNanos);
            consecutiveSuccess++;
            
            // Track time-to-first-frame
            if (waitingForFirstFrame && trackStartTime > 0) {
                firstFrameTime = currentTimeMs;
                lastTimeToFirstFrameMs = firstFrameTime - trackStartTime;
                waitingForFirstFrame = false;
            }
            
            // End of a stutter sequence - record it (rare path, allocation OK)
            if (consecutiveMisses > 0) {
                recordStutterEvent(consecutiveMisses, lastMissTimestamp);
                consecutiveMisses = 0;
            }
        } else {
            totalFramesMissed.incrementAndGet();
            consecutiveMisses++;
            consecutiveSuccess = 0;
            lastMissTimestamp = currentTimeMs;
        }
        
        // Write to ring buffer (O(1), no allocation)
        int index = ringHead.getAndUpdate(i -> (i + 1) % MAX_HISTORY_SIZE);
        timestamps[index] = currentTimeMs;
        frameProvided[index] = provided;
        latencyMs[index] = provisionLatencyNanos / 1_000_000.0f;
        // Clamp cadence delta to short range to save memory
        cadenceDeltaMs[index] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, cadenceDelta));
        
        // Track how many frames we have (up to MAX_HISTORY_SIZE)
        if (frameCount < MAX_HISTORY_SIZE) {
            frameCount++;
        }
    }
    
    /**
     * Records a stutter event (sequence of consecutive missed frames).
     * Called rarely, so allocation is acceptable.
     */
    private void recordStutterEvent(int missedFrames, long timestamp) {
        stutterCount.incrementAndGet();
        int durationMs = missedFrames * 20; // Each frame is 20ms
        
        stutterEvents.addLast(new StutterEvent(timestamp, missedFrames, durationMs));
        
        while (stutterEvents.size() > MAX_STUTTER_EVENTS) {
            stutterEvents.pollFirst();
        }
    }
    
    /**
     * Records a Lavaplayer track stuck event.
     * Called when Lavaplayer's decoder/stream stalls and can't provide audio.
     *
     * @param thresholdMs the threshold (in ms) that was exceeded before the stuck event triggered
     * @param trackTitle the title of the stuck track (may be null)
     * @param trackUri the URI of the stuck track (may be null)
     */
    public void recordStuckEvent(long thresholdMs, String trackTitle, String trackUri) {
        stuckCount.incrementAndGet();
        long timestamp = System.currentTimeMillis();
        
        stuckEvents.addLast(new StuckEvent(timestamp, thresholdMs, trackTitle, trackUri));
        
        while (stuckEvents.size() > MAX_STUCK_EVENTS) {
            stuckEvents.pollFirst();
        }
        
        // Also record as a track event for timeline
        recordTrackEvent(TrackEventType.STUCK, trackTitle, trackUri);
    }
    
    /**
     * Records a track lifecycle event.
     *
     * @param type the event type
     * @param trackTitle the track title (may be null)
     * @param trackUri the track URI (may be null)
     */
    public void recordTrackEvent(TrackEventType type, String trackTitle, String trackUri) {
        long timestamp = System.currentTimeMillis();
        trackEvents.addLast(new TrackEvent(timestamp, type, trackTitle, trackUri));
        
        while (trackEvents.size() > MAX_TRACK_EVENTS) {
            trackEvents.pollFirst();
        }
    }
    
    /**
     * Records a track start event and begins time-to-first-frame tracking.
     *
     * @param trackTitle the track title
     * @param trackUri the track URI
     */
    public void recordTrackStart(String trackTitle, String trackUri) {
        trackStartTime = System.currentTimeMillis();
        firstFrameTime = 0;
        waitingForFirstFrame = true;
        recordTrackEvent(TrackEventType.STARTED, trackTitle, trackUri);
    }
    
    /**
     * Records a track end event.
     *
     * @param trackTitle the track title
     * @param trackUri the track URI
     */
    public void recordTrackEnd(String trackTitle, String trackUri) {
        waitingForFirstFrame = false;
        recordTrackEvent(TrackEventType.ENDED, trackTitle, trackUri);
    }
    
    /**
     * Records a track exception event.
     *
     * @param trackTitle the track title
     * @param trackUri the track URI
     */
    public void recordTrackException(String trackTitle, String trackUri) {
        waitingForFirstFrame = false;
        recordTrackEvent(TrackEventType.EXCEPTION, trackTitle, trackUri);
    }
    
    /**
     * Gets recent track events.
     *
     * @param windowSeconds how many seconds of history to return
     * @return array of track events within the window
     */
    public TrackEvent[] getRecentTrackEvents(int windowSeconds) {
        long cutoff = System.currentTimeMillis() - (windowSeconds * 1000L);
        return trackEvents.stream()
            .filter(e -> e.timestamp() >= cutoff)
            .toArray(TrackEvent[]::new);
    }
    
    /**
     * Gets the time-to-first-frame for the most recent track in milliseconds.
     *
     * @return time in ms between track start and first audio frame, or 0 if not yet recorded
     */
    public long getTimeToFirstFrameMs() {
        return lastTimeToFirstFrameMs;
    }
    
    /**
     * Gets the total count of stuck events in the current session.
     */
    public long getStuckCount() {
        return stuckCount.get();
    }
    
    /**
     * Gets recent stuck events.
     *
     * @param windowSeconds how many seconds of history to return
     * @return array of stuck events within the window
     */
    public StuckEvent[] getRecentStuckEvents(int windowSeconds) {
        long cutoff = System.currentTimeMillis() - (windowSeconds * 1000L);
        return stuckEvents.stream()
            .filter(e -> e.timestamp() >= cutoff)
            .toArray(StuckEvent[]::new);
    }
    
    /**
     * Resets session counters (called when track starts).
     */
    public void resetSession() {
        totalFramesRequested.set(0);
        totalFramesProvided.set(0);
        totalFramesMissed.set(0);
        totalLatencyNanos.set(0);
        stutterCount.set(0);
        stuckCount.set(0);
        consecutiveMisses = 0;
        consecutiveSuccess = 0;
        lastMissTimestamp = 0;
        frameCount = 0;
        ringHead.set(0);
        sessionStartTime = System.currentTimeMillis();
        stutterEvents.clear();
        stuckEvents.clear();
        // Reset cadence tracking
        lastFrameRequestTime = 0;
        totalCadenceDeltaMs.set(0);
        lateFrameCount.set(0);
        maxCadenceDeltaMs = 0;
    }
    
    /**
     * Gets a snapshot of current metrics for display.
     * This is called on the UI thread and does all the heavy computation.
     *
     * @param graphWindowSeconds how many seconds of history to include
     */
    public MetricsSnapshot getSnapshot(int graphWindowSeconds) {
        long requested = totalFramesRequested.get();
        long provided = totalFramesProvided.get();
        long missed = totalFramesMissed.get();
        long stutters = stutterCount.get();
        int currentStreak = consecutiveSuccess;
        
        double avgLatencyMs = provided > 0 
            ? (totalLatencyNanos.get() / (double) provided) / 1_000_000.0 
            : 0;
        
        double missRate = requested > 0 
            ? (missed * 100.0) / requested 
            : 0;
        
        // Calculate how many frames for the requested window (50 fps)
        int graphPoints = Math.min(graphWindowSeconds * 50, frameCount);
        
        // Copy frame data from ring buffer (on UI thread, OK to allocate)
        FrameMetric[] frameHistory = extractFrameHistory(graphPoints);
        
        // Get stutter events
        StutterEvent[] stutterArray = stutterEvents.toArray(new StutterEvent[0]);
        
        // Get stuck events (Lavaplayer track stuck)
        StuckEvent[] stuckArray = getRecentStuckEvents(graphWindowSeconds);
        long stucks = stuckCount.get();
        
        // Calculate latency buckets for line graph (100ms buckets)
        LatencyBucket[] latencyBuckets = computeLatencyBuckets(frameHistory);
        
        // Calculate quality history (1-second chunks)
        QualityPoint[] qualityHistory = computeQualityHistory(frameHistory);
        
        // Calculate recent stats (last 50 frames = 1 second)
        int recentMissed = countRecentMisses(50);
        
        // Calculate frames per second
        long durationMs = System.currentTimeMillis() - sessionStartTime;
        double framesPerSecond = durationMs > 0 ? (requested * 1000.0) / durationMs : 0;
        
        // Buffer health based on consecutive success streak
        int bufferHealth = Math.min(100, currentStreak * 2); // 50+ consecutive = 100%
        
        // Get GC events from GCMonitor
        GCMonitor.GCEvent[] gcEvents = GCMonitor.getInstance().getRecentEvents(graphWindowSeconds);
        
        // Cadence statistics
        long lateFrames = lateFrameCount.get();
        long maxCadence = maxCadenceDeltaMs;
        double avgCadenceDelta = requested > 1 ? (double) totalCadenceDeltaMs.get() / (requested - 1) : 0;
        
        // Calculate p95 latency
        double p95Latency = computeP95Latency(frameHistory);
        
        // Calculate miss rate windows
        double missRate10 = getMissRate10s();
        double missRate60 = getMissRate60s();
        
        // Get track events
        TrackEvent[] trackEventArray = getRecentTrackEvents(graphWindowSeconds);
        
        // Get time-to-first-frame
        long ttff = lastTimeToFirstFrameMs;
        
        return new MetricsSnapshot(
            guildId,
            durationMs,
            requested,
            provided,
            missed,
            missRate,
            missRate10,
            missRate60,
            avgLatencyMs,
            p95Latency,
            stutters,
            stucks,
            recentMissed,
            framesPerSecond,
            graphWindowSeconds,
            bufferHealth,
            currentStreak,
            lateFrames,
            avgCadenceDelta,
            maxCadence,
            ttff,
            frameHistory,
            stutterArray,
            stuckArray,
            trackEventArray,
            latencyBuckets,
            qualityHistory,
            gcEvents
        );
    }
    
    /**
     * Extracts frame history from ring buffer.
     */
    private FrameMetric[] extractFrameHistory(int count) {
        if (count <= 0 || frameCount == 0) {
            return new FrameMetric[0];
        }
        
        int actualCount = Math.min(count, frameCount);
        FrameMetric[] result = new FrameMetric[actualCount];
        
        int head = ringHead.get();
        int start = (head - actualCount + MAX_HISTORY_SIZE) % MAX_HISTORY_SIZE;
        
        for (int i = 0; i < actualCount; i++) {
            int idx = (start + i) % MAX_HISTORY_SIZE;
            result[i] = new FrameMetric(timestamps[idx], frameProvided[idx], latencyMs[idx], cadenceDeltaMs[idx]);
        }
        
        return result;
    }
    
    /**
     * Counts recent missed frames from the ring buffer.
     */
    private int countRecentMisses(int frameCount) {
        int count = Math.min(frameCount, this.frameCount);
        if (count == 0) return 0;
        
        int missed = 0;
        int head = ringHead.get();
        
        for (int i = 0; i < count; i++) {
            int idx = (head - 1 - i + MAX_HISTORY_SIZE) % MAX_HISTORY_SIZE;
            if (!frameProvided[idx]) {
                missed++;
            }
        }
        
        return missed;
    }
    
    /**
     * Calculates miss rate for a specific frame window.
     *
     * @param framesToCheck number of frames to analyze
     * @return miss rate as a percentage (0-100)
     */
    private double calculateMissRate(int framesToCheck) {
        int count = Math.min(framesToCheck, this.frameCount);
        if (count == 0) return 0;
        
        int missed = countRecentMisses(count);
        return (missed * 100.0) / count;
    }
    
    /**
     * Gets the miss rate for the last 10 seconds (500 frames).
     */
    public double getMissRate10s() {
        return calculateMissRate(FRAMES_10_SECONDS);
    }
    
    /**
     * Gets the miss rate for the last 60 seconds (3000 frames).
     */
    public double getMissRate60s() {
        return calculateMissRate(FRAMES_60_SECONDS);
    }
    
    /**
     * Computes latency buckets for line graph (aggregated every 100ms).
     */
    private LatencyBucket[] computeLatencyBuckets(FrameMetric[] frames) {
        if (frames.length == 0) {
            return new LatencyBucket[0];
        }
        
        List<LatencyBucket> buckets = new ArrayList<>();
        long bucketSize = 100; // 100ms per bucket
        
        long bucketTimestamp = frames[0].timestamp();
        double min = Double.MAX_VALUE, max = 0, sum = 0;
        int count = 0;
        
        for (int i = 0; i < frames.length; i++) {
            FrameMetric frame = frames[i];
            
            // Check if we've moved to a new bucket
            if (frame.timestamp() >= bucketTimestamp + bucketSize && count > 0) {
                buckets.add(new LatencyBucket(bucketTimestamp, min, max, sum / count));
                bucketTimestamp = frame.timestamp();
                min = Double.MAX_VALUE;
                max = 0;
                sum = 0;
                count = 0;
            }
            
            if (frame.frameProvided()) {
                double lat = frame.latencyMs();
                min = Math.min(min, lat);
                max = Math.max(max, lat);
                sum += lat;
                count++;
            }
        }
        
        // Add final bucket
        if (count > 0) {
            buckets.add(new LatencyBucket(bucketTimestamp, min, max, sum / count));
        }
        
        return buckets.toArray(new LatencyBucket[0]);
    }
    
    /**
     * Computes quality score history (sampled every 1 second).
     */
    private QualityPoint[] computeQualityHistory(FrameMetric[] frames) {
        if (frames.length == 0) {
            return new QualityPoint[0];
        }
        
        List<QualityPoint> points = new ArrayList<>();
        long chunkSize = 1000; // 1 second per point
        
        long chunkTimestamp = frames[0].timestamp();
        int provided = 0, missed = 0;
        double latencySum = 0;
        
        for (FrameMetric frame : frames) {
            if (frame.timestamp() >= chunkTimestamp + chunkSize) {
                // Compute quality for this chunk
                int score = computeChunkQuality(provided, missed, latencySum / Math.max(1, provided));
                points.add(new QualityPoint(chunkTimestamp, score));
                
                chunkTimestamp = frame.timestamp();
                provided = 0;
                missed = 0;
                latencySum = 0;
            }
            
            if (frame.frameProvided()) {
                provided++;
                latencySum += frame.latencyMs();
            } else {
                missed++;
            }
        }
        
        // Add final chunk
        if (provided + missed > 0) {
            int score = computeChunkQuality(provided, missed, latencySum / Math.max(1, provided));
            points.add(new QualityPoint(chunkTimestamp, score));
        }
        
        return points.toArray(new QualityPoint[0]);
    }
    
    private int computeChunkQuality(int provided, int missed, double avgLatency) {
        int total = provided + missed;
        if (total == 0) return 100;
        
        double missRate = (missed * 100.0) / total;
        double score = 100.0;
        score -= Math.min(50, missRate * 10);
        score -= Math.min(30, missed * 6);
        if (avgLatency > 5) {
            score -= Math.min(20, (avgLatency - 5) * 2);
        }
        
        return (int) Math.max(0, Math.min(100, score));
    }
    
    /**
     * Computes the p95 (95th percentile) latency from frame history.
     * p95 latency means 95% of frames were processed faster than this value.
     *
     * @param frames the frame history to analyze
     * @return p95 latency in milliseconds, or 0 if insufficient data
     */
    private double computeP95Latency(FrameMetric[] frames) {
        if (frames.length < 20) {
            return 0; // Need at least 20 samples for meaningful percentile
        }
        
        // Extract latencies from provided frames only
        float[] latencies = new float[frames.length];
        int count = 0;
        for (FrameMetric frame : frames) {
            if (frame.frameProvided()) {
                latencies[count++] = frame.latencyMs();
            }
        }
        
        if (count < 20) {
            return 0;
        }
        
        // Sort and find 95th percentile
        float[] validLatencies = Arrays.copyOf(latencies, count);
        Arrays.sort(validLatencies);
        
        int p95Index = (int) Math.ceil(count * 0.95) - 1;
        p95Index = Math.max(0, Math.min(p95Index, count - 1));
        
        return validLatencies[p95Index];
    }
    
    public long getGuildId() {
        return guildId;
    }
    
    // ===== Record Types =====
    
    /**
     * Single frame metric data point.
     */
    public record FrameMetric(long timestamp, boolean frameProvided, float latencyMs, short cadenceDeltaMs) {
        /**
         * Returns true if this frame was "late" (cadence exceeded threshold).
         */
        public boolean isLate() {
            return cadenceDeltaMs > LATE_THRESHOLD_MS;
        }
    }
    
    /**
     * Latency statistics for a time bucket (100ms).
     */
    public record LatencyBucket(long timestamp, double min, double max, double avg) {}
    
    /**
     * Quality score at a point in time.
     */
    public record QualityPoint(long timestamp, int score) {}
    
    /**
     * Represents a stutter event (one or more consecutive missed frames).
     */
    public record StutterEvent(long timestamp, int missedFrames, int durationMs) {
        public Severity severity() {
            if (durationMs >= 200) return Severity.SEVERE;
            if (durationMs >= 100) return Severity.MODERATE;
            if (durationMs >= 40) return Severity.MINOR;
            return Severity.MINIMAL;
        }
        
        public enum Severity {
            MINIMAL, MINOR, MODERATE, SEVERE
        }
    }
    
    /**
     * Types of track lifecycle events.
     */
    public enum TrackEventType {
        STARTED, ENDED, EXCEPTION, STUCK
    }
    
    /**
     * Represents a track lifecycle event for timeline visualization.
     */
    public record TrackEvent(long timestamp, TrackEventType type, String trackTitle, String trackUri) {
        /**
         * Returns a short display string for the event type.
         */
        public String typeSymbol() {
            return switch (type) {
                case STARTED -> "▶";
                case ENDED -> "■";
                case EXCEPTION -> "⚠";
                case STUCK -> "✕";
            };
        }
    }
    
    /**
     * Represents a Lavaplayer track stuck event (decoder/stream stall).
     * These indicate the audio source couldn't provide data for the threshold duration.
     */
    public record StuckEvent(long timestamp, long thresholdMs, String trackTitle, String trackUri) {
        /**
         * Returns severity based on threshold duration.
         */
        public Severity severity() {
            if (thresholdMs >= 20000) return Severity.SEVERE;
            if (thresholdMs >= 10000) return Severity.MODERATE;
            return Severity.MINOR;
        }
        
        public enum Severity {
            MINOR, MODERATE, SEVERE
        }
    }
    
    /**
     * Snapshot of current metrics for UI display.
     */
    public record MetricsSnapshot(
        long guildId,
        long sessionDurationMs,
        long totalFramesRequested,
        long totalFramesProvided,
        long totalFramesMissed,
        double missRatePercent,
        double missRate10s,
        double missRate60s,
        double avgLatencyMs,
        double p95LatencyMs,
        long stutterCount,
        long stuckCount,
        int recentFramesMissed,
        double framesPerSecond,
        int graphWindowSeconds,
        int bufferHealth,
        int consecutiveStreak,
        long lateFrameCount,
        double avgCadenceDeltaMs,
        long maxCadenceDeltaMs,
        long timeToFirstFrameMs,
        FrameMetric[] frameHistory,
        StutterEvent[] stutterEvents,
        StuckEvent[] stuckEvents,
        TrackEvent[] trackEvents,
        LatencyBucket[] latencyBuckets,
        QualityPoint[] qualityHistory,
        GCMonitor.GCEvent[] gcEvents
    ) {
        public String formattedDuration() {
            long seconds = sessionDurationMs / 1000;
            long minutes = seconds / 60;
            seconds = seconds % 60;
            return String.format("%d:%02d", minutes, seconds);
        }
        
        public HealthStatus healthStatus() {
            if (missRatePercent > 5 || recentFramesMissed > 3 || stuckCount > 0) {
                return HealthStatus.CRITICAL;
            }
            if (missRatePercent > 1 || recentFramesMissed > 0 || stutterCount > 0 || maxCadenceDeltaMs > 50) {
                return HealthStatus.WARNING;
            }
            return HealthStatus.GOOD;
        }
        
        public int qualityScore() {
            double score = 100.0;
            score -= Math.min(50, missRatePercent * 10);
            score -= Math.min(30, recentFramesMissed * 6);
            score -= Math.min(20, stuckCount * 10); // Stuck events heavily impact quality
            if (avgLatencyMs > 5) {
                score -= Math.min(20, (avgLatencyMs - 5) * 2);
            }
            // Penalize high jitter/late frames
            if (maxCadenceDeltaMs > 50) {
                score -= Math.min(10, (maxCadenceDeltaMs - 50) / 10.0);
            }
            return (int) Math.max(0, Math.min(100, score));
        }
        
        /**
         * Returns true if there are cadence/jitter issues.
         */
        public boolean hasJitterIssues() {
            return maxCadenceDeltaMs > 30 || avgCadenceDeltaMs > 5;
        }
        
        private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
        private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());
        
        /**
         * Exports this snapshot to JSON for diagnostics sharing.
         *
         * @return JSON string representation
         */
        public String toJson() {
            ObjectNode root = MAPPER.createObjectNode();
            
            // Metadata
            root.put("exportTime", TIME_FMT.format(Instant.now()));
            root.put("guildId", guildId);
            root.put("windowSeconds", graphWindowSeconds);
            
            // Summary
            ObjectNode summary = root.putObject("summary");
            summary.put("healthStatus", healthStatus().name());
            summary.put("qualityScore", qualityScore());
            summary.put("duration", formattedDuration());
            summary.put("sessionDurationMs", sessionDurationMs);
            
            // Frame Stats
            ObjectNode frames = root.putObject("frameStats");
            frames.put("requested", totalFramesRequested);
            frames.put("provided", totalFramesProvided);
            frames.put("missed", totalFramesMissed);
            frames.put("missRatePercent", Math.round(missRatePercent * 100) / 100.0);
            frames.put("missRate10s", Math.round(missRate10s * 100) / 100.0);
            frames.put("missRate60s", Math.round(missRate60s * 100) / 100.0);
            frames.put("avgLatencyMs", Math.round(avgLatencyMs * 100) / 100.0);
            frames.put("p95LatencyMs", Math.round(p95LatencyMs * 100) / 100.0);
            frames.put("framesPerSecond", Math.round(framesPerSecond * 10) / 10.0);
            frames.put("bufferHealth", bufferHealth);
            frames.put("consecutiveStreak", consecutiveStreak);
            frames.put("recentMissed", recentFramesMissed);
            frames.put("timeToFirstFrameMs", timeToFirstFrameMs);
            
            // Cadence/Jitter Stats
            ObjectNode cadence = root.putObject("cadenceStats");
            cadence.put("lateFrameCount", lateFrameCount);
            cadence.put("avgCadenceDeltaMs", Math.round(avgCadenceDeltaMs * 100) / 100.0);
            cadence.put("maxCadenceDeltaMs", maxCadenceDeltaMs);
            cadence.put("hasJitterIssues", hasJitterIssues());
            
            // Event counts
            ObjectNode events = root.putObject("eventCounts");
            events.put("stutters", stutterCount);
            events.put("stuckEvents", stuckCount);
            events.put("trackEvents", trackEvents.length);
            events.put("gcEvents", gcEvents.length);
            events.put("lateFrames", lateFrameCount);
            
            // Stutter events
            ArrayNode stutterArr = root.putArray("stutterEvents");
            for (StutterEvent e : stutterEvents) {
                ObjectNode node = stutterArr.addObject();
                node.put("timestamp", e.timestamp());
                node.put("time", TIME_FMT.format(Instant.ofEpochMilli(e.timestamp())));
                node.put("missedFrames", e.missedFrames());
                node.put("durationMs", e.durationMs());
                node.put("severity", e.severity().name());
            }
            
            // Stuck events
            ArrayNode stuckArr = root.putArray("stuckEvents");
            for (StuckEvent e : stuckEvents) {
                ObjectNode node = stuckArr.addObject();
                node.put("timestamp", e.timestamp());
                node.put("time", TIME_FMT.format(Instant.ofEpochMilli(e.timestamp())));
                node.put("thresholdMs", e.thresholdMs());
                node.put("trackTitle", e.trackTitle());
                node.put("trackUri", e.trackUri());
                node.put("severity", e.severity().name());
            }
            
            // Track events
            ArrayNode trackArr = root.putArray("trackEvents");
            for (TrackEvent e : trackEvents) {
                ObjectNode node = trackArr.addObject();
                node.put("timestamp", e.timestamp());
                node.put("time", TIME_FMT.format(Instant.ofEpochMilli(e.timestamp())));
                node.put("type", e.type().name());
                node.put("trackTitle", e.trackTitle());
                node.put("trackUri", e.trackUri());
            }
            
            // GC events
            ArrayNode gcArr = root.putArray("gcEvents");
            for (GCMonitor.GCEvent e : gcEvents) {
                ObjectNode node = gcArr.addObject();
                node.put("timestamp", e.timestamp());
                node.put("time", TIME_FMT.format(Instant.ofEpochMilli(e.timestamp())));
                node.put("collector", e.collectorName());
                node.put("collections", e.collections());
                node.put("durationMs", e.durationMs());
                node.put("severity", e.severity().name());
            }
            
            // Latency buckets (for charting)
            ArrayNode latencyArr = root.putArray("latencyBuckets");
            for (LatencyBucket b : latencyBuckets) {
                ObjectNode node = latencyArr.addObject();
                node.put("timestamp", b.timestamp());
                node.put("min", Math.round(b.min() * 100) / 100.0);
                node.put("max", Math.round(b.max() * 100) / 100.0);
                node.put("avg", Math.round(b.avg() * 100) / 100.0);
            }
            
            // Quality history (for charting)
            ArrayNode qualityArr = root.putArray("qualityHistory");
            for (QualityPoint p : qualityHistory) {
                ObjectNode node = qualityArr.addObject();
                node.put("timestamp", p.timestamp());
                node.put("score", p.score());
            }
            
            try {
                return MAPPER.writeValueAsString(root);
            } catch (JsonProcessingException e) {
                return "{\"error\": \"Failed to serialize metrics\"}";
            }
        }
        
        /**
         * Generates a compact summary suitable for Discord posts.
         *
         * @return formatted summary string
         */
        public String toDiscordSummary() {
            StringBuilder sb = new StringBuilder();
            sb.append("**JMusicBot Audio Diagnostics**\n");
            sb.append("```\n");
            sb.append("Health: ").append(healthStatus().name())
              .append(" | Quality: ").append(qualityScore()).append("%\n");
            sb.append("Duration: ").append(formattedDuration())
              .append(" | Window: ").append(graphWindowSeconds).append("s\n");
            sb.append("\n");
            sb.append("Frames: ").append(totalFramesProvided).append(" provided, ")
              .append(totalFramesMissed).append(" missed\n");
            sb.append("Miss Rate: ").append(String.format("%.2f%%", missRatePercent));
            sb.append(" (10s: ").append(String.format("%.2f%%", missRate10s));
            sb.append(", 60s: ").append(String.format("%.2f%%", missRate60s)).append(")\n");
            sb.append("Avg Latency: ").append(String.format("%.2f ms", avgLatencyMs))
              .append(" | p95: ").append(String.format("%.2f ms", p95LatencyMs)).append("\n");
            sb.append("Buffer Health: ").append(bufferHealth).append("%\n");
            if (timeToFirstFrameMs > 0) {
                sb.append("Time to First Frame: ").append(timeToFirstFrameMs).append("ms\n");
            }
            sb.append("\n");
            sb.append("Stutters: ").append(stutterCount).append("\n");
            sb.append("Stuck Events: ").append(stuckCount).append("\n");
            sb.append("Track Events: ").append(trackEvents.length).append("\n");
            sb.append("GC Events: ").append(gcEvents.length).append("\n");
            sb.append("Late Frames: ").append(lateFrameCount)
              .append(" (max jitter: ").append(maxCadenceDeltaMs).append("ms)\n");
            sb.append("```\n");
            sb.append("*Exported: ").append(TIME_FMT.format(Instant.now())).append("*");
            return sb.toString();
        }
    }
    
    public enum HealthStatus {
        GOOD, WARNING, CRITICAL
    }
}
