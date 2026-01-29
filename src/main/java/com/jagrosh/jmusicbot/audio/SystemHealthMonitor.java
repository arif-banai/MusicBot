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

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitors JVM and system health metrics for diagnostics.
 * Tracks CPU usage, memory, and thread statistics.
 *
 * <p>Uses {@link com.sun.management.OperatingSystemMXBean} for CPU metrics
 * and {@link ThreadMXBean} for thread statistics.
 *
 * @author Arif Banai (arif-banai)
 */
public final class SystemHealthMonitor {
    
    private static final Logger LOG = LoggerFactory.getLogger(SystemHealthMonitor.class);
    private static final int MAX_SAMPLES = 600; // 10 minutes at 1s intervals
    private static final int SAMPLE_INTERVAL_MS = 1000; // Sample every second
    
    private static volatile SystemHealthMonitor instance;
    
    private final ConcurrentLinkedDeque<HealthSample> samples = new ConcurrentLinkedDeque<>();
    private final ScheduledExecutorService scheduler;
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    private final com.sun.management.OperatingSystemMXBean osBean;
    
    private volatile boolean running = false;
    private volatile long lastHeapUsed = 0;
    private volatile long lastSampleTime = 0;
    private volatile long expectedNextSampleTime = 0;
    private volatile long maxDriftMs = 0;
    private volatile long totalDriftMs = 0;
    private volatile long driftSampleCount = 0;
    
    private SystemHealthMonitor() {
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();
        
        // Get the sun.management version for CPU metrics
        var osMxBean = ManagementFactory.getOperatingSystemMXBean();
        if (osMxBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
            this.osBean = sunBean;
        } else {
            this.osBean = null;
            LOG.warn("OperatingSystemMXBean not available - CPU metrics will be unavailable");
        }
        
        // Create daemon scheduler
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "SystemHealthMonitor");
            t.setDaemon(true);
            return t;
        });
    }
    
    /**
     * Gets the singleton instance.
     */
    public static SystemHealthMonitor getInstance() {
        if (instance == null) {
            synchronized (SystemHealthMonitor.class) {
                if (instance == null) {
                    instance = new SystemHealthMonitor();
                }
            }
        }
        return instance;
    }
    
    /**
     * Starts the health monitoring thread.
     */
    public void start() {
        if (running) return;
        
        running = true;
        long now = System.currentTimeMillis();
        lastSampleTime = now;
        lastHeapUsed = getHeapUsed();
        expectedNextSampleTime = now + SAMPLE_INTERVAL_MS;
        maxDriftMs = 0;
        totalDriftMs = 0;
        driftSampleCount = 0;
        
        scheduler.scheduleAtFixedRate(this::takeSample, SAMPLE_INTERVAL_MS, SAMPLE_INTERVAL_MS, TimeUnit.MILLISECONDS);
        LOG.info("System Health Monitor started");
    }
    
    /**
     * Stops the health monitoring thread.
     */
    public void stop() {
        running = false;
        scheduler.shutdown();
        LOG.info("System Health Monitor stopped");
    }
    
    /**
     * Takes a health sample.
     */
    private void takeSample() {
        try {
            long now = System.currentTimeMillis();
            long timeDelta = now - lastSampleTime;
            
            // Calculate scheduler drift (how late this sample is)
            long schedulerDriftMs = 0;
            if (expectedNextSampleTime > 0) {
                schedulerDriftMs = now - expectedNextSampleTime;
                if (schedulerDriftMs > 0) {
                    totalDriftMs += schedulerDriftMs;
                    driftSampleCount++;
                    if (schedulerDriftMs > maxDriftMs) {
                        maxDriftMs = schedulerDriftMs;
                        if (schedulerDriftMs > 50) {
                            LOG.debug("High scheduler drift detected: {}ms", schedulerDriftMs);
                        }
                    }
                }
            }
            expectedNextSampleTime = now + SAMPLE_INTERVAL_MS;
            
            // CPU metrics
            double processCpuLoad = osBean != null ? osBean.getProcessCpuLoad() * 100 : -1;
            double systemCpuLoad = osBean != null ? osBean.getCpuLoad() * 100 : -1;
            
            // Memory metrics
            MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
            long heapUsed = heapUsage.getUsed();
            long heapMax = heapUsage.getMax();
            double heapPercent = heapMax > 0 ? (heapUsed * 100.0) / heapMax : 0;
            
            // Allocation rate (bytes per second)
            long heapDelta = heapUsed - lastHeapUsed;
            double allocationRate = timeDelta > 0 ? (heapDelta * 1000.0) / timeDelta : 0;
            
            // Thread metrics
            int threadCount = threadBean.getThreadCount();
            int peakThreadCount = threadBean.getPeakThreadCount();
            long[] deadlockedThreads = threadBean.findDeadlockedThreads();
            int deadlockedCount = deadlockedThreads != null ? deadlockedThreads.length : 0;
            
            // Create sample
            HealthSample sample = new HealthSample(
                now,
                processCpuLoad,
                systemCpuLoad,
                heapUsed,
                heapMax,
                heapPercent,
                allocationRate,
                threadCount,
                peakThreadCount,
                deadlockedCount,
                schedulerDriftMs
            );
            
            samples.addLast(sample);
            
            // Trim old samples
            while (samples.size() > MAX_SAMPLES) {
                samples.pollFirst();
            }
            
            // Update for next iteration
            lastHeapUsed = heapUsed;
            lastSampleTime = now;
            
        } catch (Exception e) {
            LOG.error("Error taking health sample", e);
        }
    }
    
    /**
     * Gets the current heap memory used.
     */
    private long getHeapUsed() {
        return memoryBean.getHeapMemoryUsage().getUsed();
    }
    
    /**
     * Gets recent health samples within the specified time window.
     *
     * @param windowSeconds how many seconds of history to return
     * @return array of health samples
     */
    public HealthSample[] getRecentSamples(int windowSeconds) {
        long cutoff = System.currentTimeMillis() - (windowSeconds * 1000L);
        return samples.stream()
            .filter(s -> s.timestamp() >= cutoff)
            .toArray(HealthSample[]::new);
    }
    
    /**
     * Gets the most recent health sample.
     *
     * @return latest sample or null if none available
     */
    public HealthSample getLatestSample() {
        return samples.peekLast();
    }
    
    /**
     * Gets all recorded samples.
     */
    public HealthSample[] getAllSamples() {
        return samples.toArray(new HealthSample[0]);
    }
    
    /**
     * Clears all recorded samples.
     */
    public void clear() {
        samples.clear();
    }
    
    /**
     * Gets the maximum scheduler drift observed since start.
     */
    public long getMaxDriftMs() {
        return maxDriftMs;
    }
    
    /**
     * Gets the average scheduler drift since start.
     */
    public double getAvgDriftMs() {
        return driftSampleCount > 0 ? (double) totalDriftMs / driftSampleCount : 0;
    }
    
    /**
     * Returns whether CPU metrics are available.
     */
    public boolean isCpuMetricsAvailable() {
        return osBean != null;
    }
    
    /**
     * Gets a snapshot summary of current system health.
     */
    public HealthSnapshot getSnapshot(int windowSeconds) {
        HealthSample[] windowSamples = getRecentSamples(windowSeconds);
        HealthSample latest = getLatestSample();
        
        if (latest == null || windowSamples.length == 0) {
            return HealthSnapshot.EMPTY;
        }
        
        // Calculate averages and peaks
        double avgProcessCpu = 0, maxProcessCpu = 0;
        double avgSystemCpu = 0, maxSystemCpu = 0;
        double avgHeapPercent = 0, maxHeapPercent = 0;
        double avgAllocRate = 0, maxAllocRate = 0;
        int maxThreadCount = 0;
        long totalDrift = 0, maxDrift = 0;
        int driftCount = 0;
        
        for (HealthSample s : windowSamples) {
            avgProcessCpu += s.processCpuPercent();
            maxProcessCpu = Math.max(maxProcessCpu, s.processCpuPercent());
            avgSystemCpu += s.systemCpuPercent();
            maxSystemCpu = Math.max(maxSystemCpu, s.systemCpuPercent());
            avgHeapPercent += s.heapUsedPercent();
            maxHeapPercent = Math.max(maxHeapPercent, s.heapUsedPercent());
            avgAllocRate += s.allocationRateBytesPerSec();
            maxAllocRate = Math.max(maxAllocRate, s.allocationRateBytesPerSec());
            maxThreadCount = Math.max(maxThreadCount, s.threadCount());
            
            // Scheduler drift stats
            if (s.schedulerDriftMs() > 0) {
                totalDrift += s.schedulerDriftMs();
                driftCount++;
                maxDrift = Math.max(maxDrift, s.schedulerDriftMs());
            }
        }
        
        int n = windowSamples.length;
        avgProcessCpu /= n;
        avgSystemCpu /= n;
        avgHeapPercent /= n;
        avgAllocRate /= n;
        double avgDrift = driftCount > 0 ? (double) totalDrift / driftCount : 0;
        
        return new HealthSnapshot(
            latest.timestamp(),
            latest.processCpuPercent(),
            avgProcessCpu,
            maxProcessCpu,
            latest.systemCpuPercent(),
            avgSystemCpu,
            maxSystemCpu,
            latest.heapUsedBytes(),
            latest.heapMaxBytes(),
            latest.heapUsedPercent(),
            avgHeapPercent,
            maxHeapPercent,
            avgAllocRate,
            maxAllocRate,
            latest.threadCount(),
            maxThreadCount,
            latest.deadlockedThreadCount(),
            latest.schedulerDriftMs(),
            avgDrift,
            maxDrift,
            windowSamples
        );
    }
    
    /**
     * Represents a single health sample.
     */
    public record HealthSample(
        long timestamp,
        double processCpuPercent,
        double systemCpuPercent,
        long heapUsedBytes,
        long heapMaxBytes,
        double heapUsedPercent,
        double allocationRateBytesPerSec,
        int threadCount,
        int peakThreadCount,
        int deadlockedThreadCount,
        long schedulerDriftMs
    ) {
        /**
         * Returns formatted heap usage.
         */
        public String formattedHeap() {
            long usedMb = heapUsedBytes / (1024 * 1024);
            long maxMb = heapMaxBytes / (1024 * 1024);
            return usedMb + "/" + maxMb + " MB";
        }
        
        /**
         * Returns formatted allocation rate.
         */
        public String formattedAllocRate() {
            if (allocationRateBytesPerSec < 0) {
                return "N/A";
            }
            double mbPerSec = allocationRateBytesPerSec / (1024 * 1024);
            return String.format("%.1f MB/s", mbPerSec);
        }
    }
    
    /**
     * Snapshot of system health for UI display.
     */
    public record HealthSnapshot(
        long timestamp,
        double currentProcessCpu,
        double avgProcessCpu,
        double maxProcessCpu,
        double currentSystemCpu,
        double avgSystemCpu,
        double maxSystemCpu,
        long heapUsedBytes,
        long heapMaxBytes,
        double currentHeapPercent,
        double avgHeapPercent,
        double maxHeapPercent,
        double avgAllocationRate,
        double maxAllocationRate,
        int currentThreadCount,
        int maxThreadCount,
        int deadlockedThreads,
        long currentSchedulerDriftMs,
        double avgSchedulerDriftMs,
        long maxSchedulerDriftMs,
        HealthSample[] samples
    ) {
        public static final HealthSnapshot EMPTY = new HealthSnapshot(
            0, -1, -1, -1, -1, -1, -1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, new HealthSample[0]
        );
        
        public boolean isEmpty() {
            return samples.length == 0;
        }
        
        public String formattedHeap() {
            long usedMb = heapUsedBytes / (1024 * 1024);
            long maxMb = heapMaxBytes / (1024 * 1024);
            return usedMb + "/" + maxMb + " MB";
        }
        
        /**
         * Returns health status based on metrics.
         */
        public HealthStatus status() {
            if (deadlockedThreads > 0) return HealthStatus.CRITICAL;
            if (currentProcessCpu > 90 || currentHeapPercent > 90) return HealthStatus.CRITICAL;
            if (currentProcessCpu > 70 || currentHeapPercent > 80) return HealthStatus.WARNING;
            if (maxSchedulerDriftMs > 100) return HealthStatus.WARNING; // High scheduler drift
            return HealthStatus.GOOD;
        }
        
        /**
         * Returns true if scheduler drift indicates potential thread starvation.
         */
        public boolean hasHighDrift() {
            return maxSchedulerDriftMs > 50;
        }
    }
    
    public enum HealthStatus {
        GOOD, WARNING, CRITICAL
    }
}
