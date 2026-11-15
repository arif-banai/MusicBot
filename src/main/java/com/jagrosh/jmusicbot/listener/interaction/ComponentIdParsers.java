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

import org.jetbrains.annotations.Nullable;

/**
 * Parsing and validation for component IDs used by queue and history interactions.
 * Format contracts: queue_{action}_{page}_{selectedTrack}_{userId}, history_{action}_{page}_{selectedTrack}_{userId},
 * playlists_{action}_{page}_{selectedIndex}_{userId}, queue_move_select_{fromPosition}_{page}_{userId},
 * history_save_{userId}, settings_{action}_{key}_{value?}_{userId}, settings_modal_{key}_{userId},
 * settings_entity_{key}_{userId}, np_{action}, ytdlp_{one|all}_{requesterId}_{videoId},
 * lib_{action}_{ownerUserId}[_{arg}], lib_searchmodal_{ownerUserId}.
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
    private static final String LIB_PREFIX = "lib_";
    private static final String LIB_SEARCHMODAL_PREFIX = "lib_searchmodal_";
    /** Prefix of the similar-song suggestion buttons; checked before {@link #YTDLP_PREFIX}. */
    public static final String YTDLP_MATCH_PREFIX = "ytdlp_match_";
    /**
     * The now-playing "Download" button. Its action contains an underscore, so it is matched
     * literally (before {@link #parseNowPlayingButtonId}, which rejects that) rather than parsed.
     */
    public static final String NOW_PLAYING_YTDLP_SAVE = "np_ytdlp_save";

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
     * Parsed similar-song suggestion button ID: action ({@code switch} or {@code reject}),
     * requesterId, suggestionId. The old 2-part format (no suggestion id) parses with
     * {@code suggestionId = 0}, which {@code MatchSuggestions} never assigns, so it always means
     * expired (e.g. a button left over from before a restart).
     */
    public record YtDlpMatchButtonId(String action, long requesterId, long suggestionId) {
    }

    /**
     * Parsed {@code /library panel} component ID: action, the DJ who opened the panel, and the
     * trailing argument ({@code token}, or {@code token_index}), or null when the ID has none.
     * A library key or a search query never rides in the ID itself (some can exceed Discord's
     * 100-character custom-ID limit on their own); see {@link LibraryPanelTokens} and
     * {@link #parseLibTokenArg}.
     */
    public record LibComponentId(String action, long ownerUserId, @Nullable String arg) {
    }

    /** A {@code /library panel} component-ID argument: a token, and an optional index (or signed delta). */
    public record LibTokenArg(String token, @Nullable Integer index) {
    }

    /**
     * Builds a {@code /library panel} component ID with no trailing argument: {@code lib_{action}_{ownerUserId}}.
     */
    public static String libComponentId(String action, long ownerUserId) {
        return LIB_PREFIX + action + "_" + ownerUserId;
    }

    /**
     * Builds a {@code /library panel} component ID carrying a {@link LibraryPanelTokens} token:
     * {@code lib_{action}_{ownerUserId}_{token}}. The token stands in for whatever state (a
     * search query, a library key) would otherwise have to ride in the ID itself.
     */
    public static String libComponentId(String action, long ownerUserId, String token) {
        return LIB_PREFIX + action + "_" + ownerUserId + "_" + token;
    }

    /**
     * Builds a {@code /library panel} component ID carrying a token and an index (the position of
     * an entry in that token's result list) or a signed delta (page navigation):
     * {@code lib_{action}_{ownerUserId}_{token}_{index}}.
     */
    public static String libComponentId(String action, long ownerUserId, String token, int index) {
        return LIB_PREFIX + action + "_" + ownerUserId + "_" + token + "_" + index;
    }

    /**
     * Parses {@code /library panel} component IDs (buttons and select menus). Expected format:
     * {@code lib_{action}_{ownerUserId}[_{arg}]}. Only the first two underscores after the prefix
     * separate fields; everything after the owner ID (if anything) is the argument, kept whole
     * (see {@link #parseLibTokenArg} to split it into a token and an index).
     */
    public static Optional<LibComponentId> parseLibComponentId(String componentId) {
        if (componentId == null || !componentId.startsWith(LIB_PREFIX) || componentId.startsWith(LIB_SEARCHMODAL_PREFIX)) {
            return Optional.empty();
        }
        String rest = componentId.substring(LIB_PREFIX.length());
        int actionEnd = rest.indexOf('_');
        if (actionEnd <= 0) {
            return Optional.empty();
        }
        String action = rest.substring(0, actionEnd);
        String afterAction = rest.substring(actionEnd + 1);
        int ownerEnd = afterAction.indexOf('_');
        String ownerPart = ownerEnd < 0 ? afterAction : afterAction.substring(0, ownerEnd);
        String arg = ownerEnd < 0 ? null : afterAction.substring(ownerEnd + 1);
        try {
            long ownerUserId = Long.parseLong(ownerPart);
            return Optional.of(new LibComponentId(action, ownerUserId, arg));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /** Builds the library search modal ID: {@code lib_searchmodal_{ownerUserId}}. */
    public static String libSearchModalId(long ownerUserId) {
        return LIB_SEARCHMODAL_PREFIX + ownerUserId;
    }

    /** Parses the library search modal ID. Expected format: {@code lib_searchmodal_{ownerUserId}}. */
    public static Optional<Long> parseLibSearchModalOwnerId(String modalId) {
        if (modalId == null || !modalId.startsWith(LIB_SEARCHMODAL_PREFIX)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(modalId.substring(LIB_SEARCHMODAL_PREFIX.length())));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    /**
     * Splits a {@code /library panel} component-ID argument into its token and, if present, its
     * trailing index (or signed delta). The token itself (see {@link LibraryPanelTokens}) never
     * contains {@code _}, so the first underscore, if any, is exactly the split point. Empty for
     * a blank argument, or one whose index part isn't a valid integer.
     */
    public static Optional<LibTokenArg> parseLibTokenArg(@Nullable String arg) {
        if (arg == null || arg.isEmpty()) {
            return Optional.empty();
        }
        int sep = arg.indexOf('_');
        if (sep < 0) {
            return Optional.of(new LibTokenArg(arg, null));
        }
        try {
            int index = Integer.parseInt(arg.substring(sep + 1));
            return Optional.of(new LibTokenArg(arg.substring(0, sep), index));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
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

    /**
     * Builds a similar-song suggestion button ID: ytdlp_match_{action}_{requesterId}_{suggestionId}.
     */
    public static String ytDlpMatchButtonId(String action, long requesterId, long suggestionId) {
        return YTDLP_MATCH_PREFIX + action + "_" + requesterId + "_" + suggestionId;
    }

    /**
     * Parses similar-song suggestion button IDs. Expected format:
     * ytdlp_match_{switch|reject}_{requesterId}_{suggestionId}. Also accepts the old 2-part
     * format, ytdlp_match_{switch|reject}_{requesterId} (no suggestion id), parsing it with
     * {@code suggestionId = 0}.
     */
    public static Optional<YtDlpMatchButtonId> parseYtDlpMatchButtonId(String componentId) {
        if (componentId == null || !componentId.startsWith(YTDLP_MATCH_PREFIX)) {
            return Optional.empty();
        }
        String rest = componentId.substring(YTDLP_MATCH_PREFIX.length());
        int actionEnd = rest.indexOf('_');
        if (actionEnd <= 0) {
            return Optional.empty();
        }
        String action = rest.substring(0, actionEnd);
        if (!action.equals("switch") && !action.equals("reject")) {
            return Optional.empty();
        }
        String tail = rest.substring(actionEnd + 1);
        if (tail.isEmpty()) {
            return Optional.empty();
        }
        String[] parts = tail.split("_");
        try {
            if (parts.length == 1) {
                return Optional.of(new YtDlpMatchButtonId(action, Long.parseLong(parts[0]), 0L));
            }
            if (parts.length == 2) {
                return Optional.of(new YtDlpMatchButtonId(action, Long.parseLong(parts[0]), Long.parseLong(parts[1])));
            }
            return Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
