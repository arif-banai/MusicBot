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
package com.jagrosh.jmusicbot.listener.interaction;

import java.util.Optional;

/**
 * Parsing and validation for component IDs used by queue and history interactions.
 * Format contracts: queue_{action}_{page}_{selectedTrack}_{userId}, history_{action}_{page}_{selectedTrack}_{userId},
 * playlists_{action}_{page}_{selectedIndex}_{userId}, queue_move_select_{fromPosition}_{page}_{userId},
 * history_save_{userId}, settings_{action}_{key}_{value?}_{userId}, settings_modal_{key}_{userId},
 * settings_entity_{key}_{userId}, np_{action}, ytdlp_{one|all}_{requesterId}_{videoId}.
 */
public final class ComponentIdParsers {

    private static final String QUEUE_PREFIX = "queue_";
    private static final String HISTORY_PREFIX = "history_";
    private static final String PLAYLISTS_PREFIX = "playlists_";
    private static final String PLAYLIST_DETAILS_PREFIX = "playlistdetails_";
    private static final String PLAYLIST_DETAILS_BACK_PREFIX = "playlistdetails_back_";
    private static final String PLAYLIST_DETAILS_MOVE_SELECT_PREFIX = "playlistdetails_move_select_";
    private static final String QUEUE_MOVE_SELECT_PREFIX = "queue_move_select_";
    private static final String HISTORY_SAVE_PREFIX = "history_save_";
    private static final String SETTINGS_PREFIX = "settings_";
    private static final String SETTINGS_MODAL_PREFIX = "settings_modal_";
    private static final String SETTINGS_ENTITY_PREFIX = "settings_entity_";
    private static final String NOW_PLAYING_PREFIX = "np_";
    private static final String YTDLP_PREFIX = "ytdlp_";

    private ComponentIdParsers() {
    }

    /**
     * Parsed queue or history button ID: action, page, selectedTrack, userId.
     */
    public record PaginatedButtonId(String action, int page, int selectedTrack, long userId) {
    }

    /**
     * Parsed queue move select ID: fromPosition, page, userId.
     */
    public record QueueMoveSelectId(int fromPosition, int page, long userId) {
    }

    /**
     * Parsed playlist-details button ID:
     * playlistIndex, listPage, action, detailsPage, selectedTrack, userId.
     */
    public record PlaylistDetailsButtonId(
            int playlistIndex,
            int listPage,
            String action,
            int detailsPage,
            int selectedTrack,
            long userId
    ) {
    }

    /**
     * Parsed playlist-details back button ID: listPage, listSelectedIndex, userId.
     */
    public record PlaylistDetailsBackId(int listPage, int listSelectedIndex, long userId) {
    }

    /**
     * Parsed playlist-details move-select ID.
     */
    public record PlaylistDetailsMoveSelectId(
            int playlistIndex,
            int listPage,
            int fromTrack,
            int detailsPage,
            long userId
    ) {
    }

    /**
     * Parsed settings button ID: action, key, value (optional), userId.
     */
    public record SettingsButtonId(String action, String key, String value, long userId) {
    }

    /**
     * Parsed settings modal ID: key, userId.
     */
    public record SettingsModalId(String key, long userId) {
    }

    /**
     * Parsed settings entity-select ID: key, userId.
     */
    public record SettingsEntitySelectId(String key, Long originalPanelMessageId, long userId) {
    }

    /**
     * Parsed now-playing button ID: action.
     */
    public record NowPlayingButtonId(String action) {
    }

    /**
     * Parsed yt-dlp prompt button ID: action ({@code one} or {@code all}), requesterId, videoId.
     */
    public record YtDlpButtonId(String action, long requesterId, String videoId) {
    }

    /**
     * Parses queue_* button component ID. Expected format: queue_action_page_selectedTrack_userId.
     */
    public static Optional<PaginatedButtonId> parseQueueButtonId(String componentId) {
        if (!componentId.startsWith(QUEUE_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = componentId.split("_");
        if (parts.length < 5) {
            return Optional.empty();
        }
        try {
            String action = parts[1];
            int page = Integer.parseInt(parts[2]);
            int selectedTrack = Integer.parseInt(parts[3]);
            long userId = Long.parseLong(parts[4]);
            return Optional.of(new PaginatedButtonId(action, page, selectedTrack, userId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses history_* button component ID. Expected format: history_action_page_selectedTrack_userId.
     */
    public static Optional<PaginatedButtonId> parseHistoryButtonId(String componentId) {
        if (!componentId.startsWith(HISTORY_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = componentId.split("_");
        if (parts.length < 5) {
            return Optional.empty();
        }
        try {
            String action = parts[1];
            int page = Integer.parseInt(parts[2]);
            int selectedTrack = Integer.parseInt(parts[3]);
            long userId = Long.parseLong(parts[4]);
            return Optional.of(new PaginatedButtonId(action, page, selectedTrack, userId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses playlists_* button component ID. Expected format: playlists_action_page_selectedIndex_userId.
     */
    public static Optional<PaginatedButtonId> parsePlaylistsButtonId(String componentId) {
        if (!componentId.startsWith(PLAYLISTS_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = componentId.split("_");
        if (parts.length < 5) {
            return Optional.empty();
        }
        try {
            String action = parts[1];
            int page = Integer.parseInt(parts[2]);
            int selectedIndex = Integer.parseInt(parts[3]);
            long userId = Long.parseLong(parts[4]);
            return Optional.of(new PaginatedButtonId(action, page, selectedIndex, userId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses playlistdetails_* button IDs except back.
     * Expected format:
     * playlistdetails_playlistIndex_listPage_action_detailsPage_selectedTrack_userId.
     */
    public static Optional<PlaylistDetailsButtonId> parsePlaylistDetailsButtonId(String componentId) {
        if (!componentId.startsWith(PLAYLIST_DETAILS_PREFIX)
                || componentId.startsWith(PLAYLIST_DETAILS_BACK_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = componentId.split("_");
        if (parts.length != 7) {
            return Optional.empty();
        }
        try {
            int playlistIndex = Integer.parseInt(parts[1]);
            int listPage = Integer.parseInt(parts[2]);
            String action = parts[3];
            int detailsPage = Integer.parseInt(parts[4]);
            int selectedTrack = Integer.parseInt(parts[5]);
            long userId = Long.parseLong(parts[6]);
            return Optional.of(new PlaylistDetailsButtonId(
                    playlistIndex, listPage, action, detailsPage, selectedTrack, userId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses playlistdetails_back_* IDs.
     * Expected format: playlistdetails_back_listPage_listSelectedIndex_userId.
     */
    public static Optional<PlaylistDetailsBackId> parsePlaylistDetailsBackId(String componentId) {
        if (!componentId.startsWith(PLAYLIST_DETAILS_BACK_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = componentId.split("_");
        if (parts.length != 5) {
            return Optional.empty();
        }
        try {
            int listPage = Integer.parseInt(parts[2]);
            int listSelectedIndex = Integer.parseInt(parts[3]);
            long userId = Long.parseLong(parts[4]);
            return Optional.of(new PlaylistDetailsBackId(listPage, listSelectedIndex, userId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses playlistdetails_move_select_* IDs.
     * Expected format: playlistdetails_move_select_playlistIndex_listPage_fromTrack_detailsPage_userId.
     */
    public static Optional<PlaylistDetailsMoveSelectId> parsePlaylistDetailsMoveSelectId(String componentId) {
        if (!componentId.startsWith(PLAYLIST_DETAILS_MOVE_SELECT_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = componentId.split("_");
        if (parts.length != 8) {
            return Optional.empty();
        }
        try {
            int playlistIndex = Integer.parseInt(parts[3]);
            int listPage = Integer.parseInt(parts[4]);
            int fromTrack = Integer.parseInt(parts[5]);
            int detailsPage = Integer.parseInt(parts[6]);
            long userId = Long.parseLong(parts[7]);
            return Optional.of(new PlaylistDetailsMoveSelectId(
                    playlistIndex, listPage, fromTrack, detailsPage, userId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses queue_move_select_* component ID. Expected format: queue_move_select_fromPosition_page_userId.
     */
    public static Optional<QueueMoveSelectId> parseQueueMoveSelectId(String componentId) {
        if (!componentId.startsWith(QUEUE_MOVE_SELECT_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = componentId.split("_");
        if (parts.length < 6) {
            return Optional.empty();
        }
        try {
            int fromPosition = Integer.parseInt(parts[3]);
            int page = Integer.parseInt(parts[4]);
            long userId = Long.parseLong(parts[5]);
            return Optional.of(new QueueMoveSelectId(fromPosition, page, userId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses history_save_* modal ID. Expected format: history_save_userId.
     */
    public static Optional<Long> parseHistorySaveModalUserId(String modalId) {
        if (!modalId.startsWith(HISTORY_SAVE_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(modalId.substring(HISTORY_SAVE_PREFIX.length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses settings button IDs.
     * Supported formats:
     * - settings_enum_key_value_userId
     * - settings_open_key_userId
     * - settings_clear_key_userId
     * - settings_refresh_key_userId
     */
    public static Optional<SettingsButtonId> parseSettingsButtonId(String componentId) {
        if (!componentId.startsWith(SETTINGS_PREFIX) || componentId.startsWith(SETTINGS_MODAL_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = componentId.split("_");
        if (parts.length == 5) {
            try {
                String action = parts[1];
                String key = parts[2];
                String value = parts[3];
                long userId = Long.parseLong(parts[4]);
                return Optional.of(new SettingsButtonId(action, key, value, userId));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        if (parts.length == 4) {
            try {
                String action = parts[1];
                String key = parts[2];
                long userId = Long.parseLong(parts[3]);
                return Optional.of(new SettingsButtonId(action, key, null, userId));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Parses settings modal IDs.
     * Expected format: settings_modal_key_userId.
     */
    public static Optional<SettingsModalId> parseSettingsModalId(String modalId) {
        if (!modalId.startsWith(SETTINGS_MODAL_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = modalId.split("_");
        if (parts.length != 4) {
            return Optional.empty();
        }
        try {
            String key = parts[2];
            long userId = Long.parseLong(parts[3]);
            return Optional.of(new SettingsModalId(key, userId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Parses settings entity-select IDs.
     * Expected format: settings_entity_key_userId.
     * New format: settings_entity_key_originalPanelMessageId_userId.
     */
    public static Optional<SettingsEntitySelectId> parseSettingsEntitySelectId(String componentId) {
        if (!componentId.startsWith(SETTINGS_ENTITY_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = componentId.split("_");
        if (parts.length == 4) {
            try {
                String key = parts[2];
                long userId = Long.parseLong(parts[3]);
                return Optional.of(new SettingsEntitySelectId(key, null, userId));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        if (parts.length == 5) {
            try {
                String key = parts[2];
                long originalPanelMessageId = Long.parseLong(parts[3]);
                long userId = Long.parseLong(parts[4]);
                return Optional.of(new SettingsEntitySelectId(key, originalPanelMessageId, userId));
            } catch (NumberFormatException e) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /**
     * Parses now-playing button IDs.
     * Expected format: np_action.
     */
    public static Optional<NowPlayingButtonId> parseNowPlayingButtonId(String componentId) {
        if (!componentId.startsWith(NOW_PLAYING_PREFIX)) {
            return Optional.empty();
        }
        String[] parts = componentId.split("_");
        if (parts.length != 2 || parts[1].isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new NowPlayingButtonId(parts[1]));
    }

    /**
     * Builds a yt-dlp prompt button ID: ytdlp_{action}_{requesterId}_{videoId}.
     * The video ID goes last because it may itself contain underscores.
     */
    public static String ytDlpButtonId(String action, long requesterId, String videoId) {
        return YTDLP_PREFIX + action + "_" + requesterId + "_" + videoId;
    }

    /**
     * Parses yt-dlp prompt button IDs. Expected format: ytdlp_{one|all}_{requesterId}_{videoId}.
     * Only the first two underscores after the prefix separate fields; everything after the
     * requester ID is the video ID, so IDs such as {@code a_b-c_d1234} round-trip exactly.
     */
    public static Optional<YtDlpButtonId> parseYtDlpButtonId(String componentId) {
        if (componentId == null || !componentId.startsWith(YTDLP_PREFIX)) {
            return Optional.empty();
        }
        String rest = componentId.substring(YTDLP_PREFIX.length());
        int actionEnd = rest.indexOf('_');
        if (actionEnd <= 0) {
            return Optional.empty();
        }
        int requesterEnd = rest.indexOf('_', actionEnd + 1);
        if (requesterEnd < 0) {
            return Optional.empty();
        }
        String action = rest.substring(0, actionEnd);
        if (!action.equals("one") && !action.equals("all")) {
            return Optional.empty();
        }
        String videoId = rest.substring(requesterEnd + 1);
        if (videoId.isEmpty()) {
            return Optional.empty();
        }
        try {
            long requesterId = Long.parseLong(rest.substring(actionEnd + 1, requesterEnd));
            return Optional.of(new YtDlpButtonId(action, requesterId, videoId));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
