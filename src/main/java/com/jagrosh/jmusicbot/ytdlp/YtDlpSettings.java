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
package com.jagrosh.jmusicbot.ytdlp;

import java.nio.file.Path;
import java.util.List;

import com.jagrosh.jmusicbot.config.model.ConfigOption;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Settings for the optional yt-dlp download library.
 * The feature is off when {@link #path()} is blank, which is the default.
 *
 * @author Arif Banai (arif-banai)
 */
public record YtDlpSettings(String path, Mode mode, Path libraryFolder, String libraryPlaylist,
                             int delaySeconds, int timeoutSeconds, List<String> extraArgs,
                             int promptTimeoutSeconds, boolean downloadButton, boolean matchingEnabled,
                             int durationToleranceSeconds, double minScore, boolean alignSwitch) {

    private static final Logger LOGGER = LoggerFactory.getLogger(YtDlpSettings.class);

    public static final boolean DEFAULT_DOWNLOAD_BUTTON = true;
    public static final boolean DEFAULT_MATCHING_ENABLED = true;
    public static final int DEFAULT_DURATION_TOLERANCE_SECONDS = 10;
    public static final double DEFAULT_MIN_SCORE = 0.85;
    /** Upper bound for {@code matching.durationToleranceSeconds}; larger values are clamped to it. */
    public static final int MAX_DURATION_TOLERANCE_SECONDS = 60;
    public static final boolean DEFAULT_ALIGN_SWITCH = true;

    /** Settings with the {@code downloadButton} and {@code matching.*} defaults. */
    public YtDlpSettings(String path, Mode mode, Path libraryFolder, String libraryPlaylist,
                         int delaySeconds, int timeoutSeconds, List<String> extraArgs, int promptTimeoutSeconds) {
        this(path, mode, libraryFolder, libraryPlaylist, delaySeconds, timeoutSeconds, extraArgs, promptTimeoutSeconds,
                DEFAULT_DOWNLOAD_BUTTON, DEFAULT_MATCHING_ENABLED, DEFAULT_DURATION_TOLERANCE_SECONDS,
                DEFAULT_MIN_SCORE, DEFAULT_ALIGN_SWITCH);
    }

    /**
     * How the bot reacts to YouTube tracks when yt-dlp is configured.
     */
    public enum Mode {
        /** Post buttons to download with yt-dlp when YouTube playback fails. */
        PROMPT,
        /** Always play YouTube tracks through yt-dlp. */
        ALWAYS;

        /**
         * Parses a mode string case-insensitively. Falls back to {@link #PROMPT} and
         * logs a warning for any value that doesn't match a known mode.
         */
        public static Mode parse(String raw) {
            String trimmed = raw == null ? "" : raw.trim();
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(trimmed)) {
                    return mode;
                }
            }
            LOGGER.warn("Unknown ytdlp.mode '{}', defaulting to 'prompt'", raw);
            return PROMPT;
        }
    }

    /**
     * Returns true if a yt-dlp path is configured. When false, the feature is
     * completely off and behaviour is unchanged from before this feature existed.
     */
    public boolean enabled() {
        return path != null && !path.isBlank();
    }

    /**
     * Returns settings representing the feature turned off, using the same
     * defaults as {@code reference.conf}.
     */
    public static YtDlpSettings disabled() {
        return new YtDlpSettings("", Mode.PROMPT, resolveLibraryFolder("ytdlp-library"),
                "ytdlp-library", 5, 300, List.of(), 120);
    }

    /**
     * Reads yt-dlp settings from the given config using the {@code ytdlp.*} keys.
     */
    public static YtDlpSettings fromConfig(Config config) {
        String path = ConfigOption.YTDLP_PATH.getString(config);
        Mode mode = Mode.parse(ConfigOption.YTDLP_MODE.getString(config));
        Path libraryFolder = resolveLibraryFolder(ConfigOption.YTDLP_LIBRARY_FOLDER.getString(config));
        String libraryPlaylist = ConfigOption.YTDLP_LIBRARY_PLAYLIST.getString(config);
        int delaySeconds = ConfigOption.YTDLP_DELAY_SECONDS.getInt(config);
        int timeoutSeconds = ConfigOption.YTDLP_TIMEOUT_SECONDS.getInt(config);
        List<String> extraArgs = ConfigOption.YTDLP_EXTRA_ARGS.getStringList(config);
        int promptTimeoutSeconds = ConfigOption.YTDLP_PROMPT_TIMEOUT_SECONDS.getInt(config);
        // Read with defaults: a config parsed without reference.conf as fallback may lack these newer keys.
        boolean downloadButton = ConfigOption.YTDLP_DOWNLOAD_BUTTON.hasValue(config)
                ? ConfigOption.YTDLP_DOWNLOAD_BUTTON.getBoolean(config) : DEFAULT_DOWNLOAD_BUTTON;
        boolean matchingEnabled = ConfigOption.YTDLP_MATCHING_ENABLED.hasValue(config)
                ? ConfigOption.YTDLP_MATCHING_ENABLED.getBoolean(config) : DEFAULT_MATCHING_ENABLED;
        int durationTolerance = ConfigOption.YTDLP_MATCHING_DURATION_TOLERANCE_SECONDS.hasValue(config)
                ? ConfigOption.YTDLP_MATCHING_DURATION_TOLERANCE_SECONDS.getInt(config)
                : DEFAULT_DURATION_TOLERANCE_SECONDS;
        double minScore = ConfigOption.YTDLP_MATCHING_MIN_SCORE.hasValue(config)
                ? ConfigOption.YTDLP_MATCHING_MIN_SCORE.getDouble(config) : DEFAULT_MIN_SCORE;
        boolean alignSwitch = ConfigOption.YTDLP_MATCHING_ALIGN_SWITCH.hasValue(config)
                ? ConfigOption.YTDLP_MATCHING_ALIGN_SWITCH.getBoolean(config) : DEFAULT_ALIGN_SWITCH;

        return new YtDlpSettings(path, mode, libraryFolder, libraryPlaylist, delaySeconds, timeoutSeconds,
                extraArgs, promptTimeoutSeconds, downloadButton, matchingEnabled,
                clampTolerance(durationTolerance), clampMinScore(minScore), alignSwitch);
    }

    private static int clampTolerance(int raw) {
        int clamped = Math.max(0, Math.min(MAX_DURATION_TOLERANCE_SECONDS, raw));
        if (clamped != raw) {
            LOGGER.warn("ytdlp.matching.durationToleranceSeconds = {} is out of range 0-{}; using {}", raw,
                    MAX_DURATION_TOLERANCE_SECONDS, clamped);
        }
        return clamped;
    }

    private static double clampMinScore(double raw) {
        double clamped = Double.isNaN(raw) ? DEFAULT_MIN_SCORE : Math.max(0.0, Math.min(1.0, raw));
        if (clamped != raw) {
            LOGGER.warn("ytdlp.matching.minScore = {} is out of range 0.0-1.0; using {}", raw, clamped);
        }
        return clamped;
    }

    /**
     * Resolves the library folder against the bot's working directory when relative.
     */
    private static Path resolveLibraryFolder(String rawPath) {
        return OtherUtil.getPath(rawPath).toAbsolutePath().normalize();
    }
}
