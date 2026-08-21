package com.jagrosh.jmusicbot.utils;

import java.util.Locale;

/**
 * Utility helper for measuring playlist execution time, calculating per-track 
 * resolution rates, and generating formatted timing summaries for logging and Discord responses.
 */
public class PlaylistExecutionTimer 
{
    private final long startTimeMs;

    private PlaylistExecutionTimer() 
    {
        this.startTimeMs = System.currentTimeMillis();
    }

    /**
     * Starts a new timer instance recorded at the current system time.
     *
     * @return a new {@link PlaylistExecutionTimer} instance
     */
    public static PlaylistExecutionTimer start() 
    {
        return new PlaylistExecutionTimer();
    }

    /**
     * Returns the elapsed time in milliseconds since the timer was started.
     *
     * @return elapsed time in milliseconds
     */
    public long getElapsedMs() 
    {
        return System.currentTimeMillis() - startTimeMs;
    }

    /**
     * Returns the elapsed time in seconds.
     *
     * @return elapsed time in seconds
     */
    public double getElapsedSeconds() 
    {
        return getElapsedMs() / 1000.0;
    }

    /**
     * Calculates average milliseconds spent per track, preventing division by zero.
     *
     * @param trackCount total number of tracks processed
     * @return average milliseconds per track, or 0 if trackCount <= 0
     */
    public long getAverageMsPerTrack(int trackCount) 
    {
        if (trackCount <= 0)
            return 0;

        return getElapsedMs() / trackCount;
    }

    /**
     * Formats a complete summary string showing total duration, track count, and average rate.
     * <p>Example output: {@code "45,000 ms ÷ 195 tracks = ~230 ms/track"}</p>
     *
     * @param trackCount total number of tracks processed
     * @return formatted rate string
     */
    public String getFormattedSummary(int trackCount) 
    {
        long totalMs = getElapsedMs();
        long avgMs = getAverageMsPerTrack(trackCount);

        return String.format(Locale.ROOT, "%,d ms ÷ %d tracks = ~%d ms/track", totalMs, trackCount, avgMs);
    }

    /**
     * Formats the elapsed time in seconds with one decimal place (e.g., {@code "4.2s"}).
     *
     * @return formatted duration string in seconds
     */
    public String getFormattedSeconds() 
    {
        return String.format(Locale.ROOT, "%.1fs", getElapsedSeconds());
    }

    /**
     * Formats the elapsed time as a markdown tag for Discord responses (e.g., {@code "`[4.2s]`"}).
     *
     * @return markdown formatted duration string
     */
    public String getFormattedTag() 
    {
        return "`[" + getFormattedSeconds() + "]`";
    }
}