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
package com.jagrosh.jmusicbot.unit.listener.interaction;

import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ComponentIdParsersTest
{
    @Test
    void nowPlayingYtDlpSave_isTheSpecifiedLiteralId()
    {
        assertEquals("np_ytdlp_save", ComponentIdParsers.NOW_PLAYING_YTDLP_SAVE);
        // Confirms why it can't go through parseNowPlayingButtonId: it has an extra underscore.
        assertTrue(ComponentIdParsers.parseNowPlayingButtonId(ComponentIdParsers.NOW_PLAYING_YTDLP_SAVE).isEmpty());
    }

    @Test
    void parsePlaylistsButtonId_validId_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parsePlaylistsButtonId("playlists_queue_2_13_123456789");
        assertTrue(parsed.isPresent());
        assertEquals("queue", parsed.get().action());
        assertEquals(2, parsed.get().page());
        assertEquals(13, parsed.get().selectedTrack());
        assertEquals(123456789L, parsed.get().userId());
    }

    @Test
    void parsePlaylistsButtonId_invalidId_returnsEmpty()
    {
        assertTrue(ComponentIdParsers.parsePlaylistsButtonId("playlists_bad").isEmpty());
        assertTrue(ComponentIdParsers.parsePlaylistsButtonId("history_queue_1_1_1").isEmpty());
    }

    @Test
    void parsePlaylistDetailsButtonId_validId_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parsePlaylistDetailsButtonId("playlistdetails_4_2_select13_2_13_123456789");
        assertTrue(parsed.isPresent());
        assertEquals(4, parsed.get().playlistIndex());
        assertEquals(2, parsed.get().listPage());
        assertEquals("select13", parsed.get().action());
        assertEquals(2, parsed.get().detailsPage());
        assertEquals(13, parsed.get().selectedTrack());
        assertEquals(123456789L, parsed.get().userId());
    }

    @Test
    void parsePlaylistDetailsButtonId_invalidId_returnsEmpty()
    {
        assertTrue(ComponentIdParsers.parsePlaylistDetailsButtonId("playlistdetails_back_1_2_3").isEmpty());
        assertTrue(ComponentIdParsers.parsePlaylistDetailsButtonId("playlistdetails_1_2_next_bad_0_3").isEmpty());
        assertTrue(ComponentIdParsers.parsePlaylistDetailsButtonId("playlists_1_2_next_1_0_3").isEmpty());
    }

    @Test
    void parsePlaylistDetailsBackId_validId_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parsePlaylistDetailsBackId("playlistdetails_back_2_4_123456789");
        assertTrue(parsed.isPresent());
        assertEquals(2, parsed.get().listPage());
        assertEquals(4, parsed.get().listSelectedIndex());
        assertEquals(123456789L, parsed.get().userId());
    }

    @Test
    void parsePlaylistDetailsBackId_invalidId_returnsEmpty()
    {
        assertTrue(ComponentIdParsers.parsePlaylistDetailsBackId("playlistdetails_back_2_x_123").isEmpty());
        assertTrue(ComponentIdParsers.parsePlaylistDetailsBackId("playlistdetails_1_2_next_1_0_3").isEmpty());
    }

    @Test
    void parsePlaylistDetailsMoveSelectId_validId_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parsePlaylistDetailsMoveSelectId("playlistdetails_move_select_4_2_7_1_123456789");
        assertTrue(parsed.isPresent());
        assertEquals(4, parsed.get().playlistIndex());
        assertEquals(2, parsed.get().listPage());
        assertEquals(7, parsed.get().fromTrack());
        assertEquals(1, parsed.get().detailsPage());
        assertEquals(123456789L, parsed.get().userId());
    }

    @Test
    void parsePlaylistDetailsMoveSelectId_invalidId_returnsEmpty()
    {
        assertTrue(ComponentIdParsers.parsePlaylistDetailsMoveSelectId("playlistdetails_move_select_4_2_7_x_123").isEmpty());
        assertTrue(ComponentIdParsers.parsePlaylistDetailsMoveSelectId("playlistdetails_4_2_move_1_7_123").isEmpty());
    }

    @Test
    void parseSettingsButtonId_enumAction_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parseSettingsButtonId("settings_enum_layout_minimal_42");
        assertTrue(parsed.isPresent());
        assertEquals("enum", parsed.get().action());
        assertEquals("layout", parsed.get().key());
        assertEquals("minimal", parsed.get().value());
        assertEquals(42L, parsed.get().userId());
    }

    @Test
    void parseSettingsButtonId_openAction_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parseSettingsButtonId("settings_open_settc_987");
        assertTrue(parsed.isPresent());
        assertEquals("open", parsed.get().action());
        assertEquals("settc", parsed.get().key());
        assertEquals(987L, parsed.get().userId());
    }

    @Test
    void parseSettingsModalId_validId_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parseSettingsModalId("settings_modal_setskip_123");
        assertTrue(parsed.isPresent());
        assertEquals("setskip", parsed.get().key());
        assertEquals(123L, parsed.get().userId());
    }

    @Test
    void parseSettingsEntitySelectId_validId_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parseSettingsEntitySelectId("settings_entity_setdj_77");
        assertTrue(parsed.isPresent());
        assertEquals("setdj", parsed.get().key());
        assertEquals(null, parsed.get().originalPanelMessageId());
        assertEquals(77L, parsed.get().userId());
    }

    @Test
    void parseSettingsEntitySelectId_withPanelMessage_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parseSettingsEntitySelectId("settings_entity_setvc_5555_77");
        assertTrue(parsed.isPresent());
        assertEquals("setvc", parsed.get().key());
        assertEquals(5555L, parsed.get().originalPanelMessageId());
        assertEquals(77L, parsed.get().userId());
    }

    @Test
    void parseSettingsIds_invalidIds_returnEmpty()
    {
        assertTrue(ComponentIdParsers.parseSettingsButtonId("settings_modal_setvc_1").isEmpty());
        assertTrue(ComponentIdParsers.parseSettingsButtonId("settings_enum_layout_minimal_bad").isEmpty());
        assertTrue(ComponentIdParsers.parseSettingsModalId("settings_modal_setvc_bad").isEmpty());
        assertTrue(ComponentIdParsers.parseSettingsModalId("settings_open_setvc_1").isEmpty());
        assertTrue(ComponentIdParsers.parseSettingsEntitySelectId("settings_entity_setvc_bad").isEmpty());
        assertTrue(ComponentIdParsers.parseSettingsEntitySelectId("settings_modal_setvc_1").isEmpty());
        assertTrue(ComponentIdParsers.parseSettingsEntitySelectId("settings_entity_setvc_1_bad").isEmpty());
    }

    @Test
    void parseNowPlayingButtonId_validId_returnsParsedValues()
    {
        var parsed = ComponentIdParsers.parseNowPlayingButtonId("np_pause");
        assertTrue(parsed.isPresent());
        assertEquals("pause", parsed.get().action());
    }

    @Test
    void parseNowPlayingButtonId_invalidId_returnsEmpty()
    {
        assertTrue(ComponentIdParsers.parseNowPlayingButtonId("pause").isEmpty());
        assertTrue(ComponentIdParsers.parseNowPlayingButtonId("np_").isEmpty());
        assertTrue(ComponentIdParsers.parseNowPlayingButtonId("np_pause_more").isEmpty());
    }

    @Test
    void parse_videoIdWithUnderscores()
    {
        var parsed = ComponentIdParsers.parseYtDlpButtonId("ytdlp_all_42_a_b-c_d1234");
        assertTrue(parsed.isPresent());
        assertEquals("all", parsed.get().action());
        assertEquals(42L, parsed.get().requesterId());
        assertEquals("a_b-c_d1234", parsed.get().videoId());
    }

    @Test
    void roundTrip_oneAndAll()
    {
        for (String action : new String[] { "one", "all" })
        {
            String id = ComponentIdParsers.ytDlpButtonId(action, 123456789012345678L, "_a-b_c__d1");
            var parsed = ComponentIdParsers.parseYtDlpButtonId(id);
            assertTrue(parsed.isPresent(), id);
            assertEquals(action, parsed.get().action());
            assertEquals(123456789012345678L, parsed.get().requesterId());
            assertEquals("_a-b_c__d1", parsed.get().videoId());
        }
    }

    @Test
    void parse_rejectsOtherPrefixes()
    {
        assertTrue(ComponentIdParsers.parseYtDlpButtonId("np_skip").isEmpty());
        assertTrue(ComponentIdParsers.parseYtDlpButtonId("ytdlp_one_notanumber_abc").isEmpty());
        assertTrue(ComponentIdParsers.parseYtDlpButtonId("ytdlp_one_42_").isEmpty());
        assertTrue(ComponentIdParsers.parseYtDlpButtonId("ytdlp_one_42").isEmpty());
        assertTrue(ComponentIdParsers.parseYtDlpButtonId("ytdlp_maybe_42_abc").isEmpty());
    }

    @Test
    void ytDlpMatch_roundTrip()
    {
        for (String action : new String[] { "switch", "reject" })
        {
            String id = ComponentIdParsers.ytDlpMatchButtonId(action, 123456789012345678L, 7L);
            assertEquals("ytdlp_match_" + action + "_123456789012345678_7", id);
            var parsed = ComponentIdParsers.parseYtDlpMatchButtonId(id);
            assertTrue(parsed.isPresent(), id);
            assertEquals(action, parsed.get().action());
            assertEquals(123456789012345678L, parsed.get().requesterId());
            assertEquals(7L, parsed.get().suggestionId());
        }
    }

    @Test
    void ytDlpMatch_oldTwoPartFormat_parsesWithSuggestionIdZero()
    {
        var parsed = ComponentIdParsers.parseYtDlpMatchButtonId("ytdlp_match_switch_123");
        assertTrue(parsed.isPresent());
        assertEquals("switch", parsed.get().action());
        assertEquals(123L, parsed.get().requesterId());
        assertEquals(0L, parsed.get().suggestionId());
    }

    @Test
    void ytDlpMatch_rejectsOthers()
    {
        assertTrue(ComponentIdParsers.parseYtDlpMatchButtonId("ytdlp_one_42_abc").isEmpty());
        assertTrue(ComponentIdParsers.parseYtDlpMatchButtonId("ytdlp_match_maybe_42").isEmpty());
        assertTrue(ComponentIdParsers.parseYtDlpMatchButtonId("ytdlp_match_switch_x").isEmpty());
        assertTrue(ComponentIdParsers.parseYtDlpMatchButtonId("ytdlp_match_switch_123_x").isEmpty());
        assertTrue(ComponentIdParsers.parseYtDlpMatchButtonId("ytdlp_match_switch_123_7_9").isEmpty());
        assertTrue(ComponentIdParsers.parseYtDlpMatchButtonId("ytdlp_match_switch").isEmpty());
        // The prompt parser doesn't take match buttons either.
        assertTrue(ComponentIdParsers.parseYtDlpButtonId("ytdlp_match_switch_42").isEmpty());
    }

    @Test
    void libComponentId_noArg_roundTrips()
    {
        String id = ComponentIdParsers.libComponentId("rescan", 123456789012345678L);
        assertEquals("lib_rescan_123456789012345678", id);
        var parsed = ComponentIdParsers.parseLibComponentId(id);
        assertTrue(parsed.isPresent(), id);
        assertEquals("rescan", parsed.get().action());
        assertEquals(123456789012345678L, parsed.get().ownerUserId());
        assertEquals(null, parsed.get().arg());
    }

    @Test
    void libComponentId_withTokenArg_roundTrips()
    {
        String token = "abc123def456";
        String id = ComponentIdParsers.libComponentId("back", 42L, token);
        assertEquals("lib_back_42_" + token, id);
        var parsed = ComponentIdParsers.parseLibComponentId(id);
        assertTrue(parsed.isPresent(), id);
        assertEquals("back", parsed.get().action());
        assertEquals(42L, parsed.get().ownerUserId());
        assertEquals(token, parsed.get().arg());
    }

    @Test
    void libComponentId_withTokenAndIndex_roundTrips()
    {
        String token = "abc123def456";
        String id = ComponentIdParsers.libComponentId("play", 42L, token, 7);
        assertEquals("lib_play_42_" + token + "_7", id);
        var parsed = ComponentIdParsers.parseLibComponentId(id);
        assertTrue(parsed.isPresent(), id);
        assertEquals("play", parsed.get().action());
        assertEquals(42L, parsed.get().ownerUserId());
        assertEquals(token + "_7", parsed.get().arg());
    }

    @Test
    void libComponentId_withToken_staysUnder100CharsEvenForTheLongestOwnerAndIndex()
    {
        // A file: key or a search query can exceed Discord's 100-char custom-ID limit on their
        // own; a token is a fixed-length, opaque UUID (see LibraryPanelTokens), so the whole
        // component ID stays well under the limit regardless of how long the underlying key or
        // query is, even at the longest possible owner ID and a multi-digit index.
        String token = java.util.UUID.randomUUID().toString().replace("-", "");
        String id = ComponentIdParsers.libComponentId("rmfile", Long.MAX_VALUE, token, -12345);
        assertTrue(id.length() <= 100, id);
        // Sanity: a 150-char file: key embedded directly, as the old scheme did, blows past the limit.
        String longKey = "file:" + "x".repeat(150);
        assertTrue(("lib_rmfile_" + Long.MAX_VALUE + "_" + longKey).length() > 100);
    }

    @Test
    void libComponentId_invalid_returnsEmpty()
    {
        assertTrue(ComponentIdParsers.parseLibComponentId(null).isEmpty());
        assertTrue(ComponentIdParsers.parseLibComponentId("lib_rescan").isEmpty());
        assertTrue(ComponentIdParsers.parseLibComponentId("lib_rescan_notanumber").isEmpty());
        assertTrue(ComponentIdParsers.parseLibComponentId("playlists_rescan_1_1_1").isEmpty());
        // The search-modal ID isn't a component ID.
        assertTrue(ComponentIdParsers.parseLibComponentId(ComponentIdParsers.libSearchModalId(1L)).isEmpty());
    }

    @Test
    void libSearchModalId_roundTrips()
    {
        String id = ComponentIdParsers.libSearchModalId(987654321L);
        assertEquals("lib_searchmodal_987654321", id);
        var parsed = ComponentIdParsers.parseLibSearchModalOwnerId(id);
        assertTrue(parsed.isPresent(), id);
        assertEquals(987654321L, parsed.get());
    }

    @Test
    void libSearchModalId_rejectsOthers()
    {
        assertTrue(ComponentIdParsers.parseLibSearchModalOwnerId(null).isEmpty());
        assertTrue(ComponentIdParsers.parseLibSearchModalOwnerId("lib_rescan_42").isEmpty());
        assertTrue(ComponentIdParsers.parseLibSearchModalOwnerId("lib_searchmodal_notanumber").isEmpty());
    }

    @Test
    void parseLibTokenArg_tokenOnly_returnsNullIndex()
    {
        var parsed = ComponentIdParsers.parseLibTokenArg("abcDEF123");
        assertTrue(parsed.isPresent());
        assertEquals("abcDEF123", parsed.get().token());
        assertEquals(null, parsed.get().index());
    }

    @Test
    void parseLibTokenArg_tokenAndIndex_splitsOnFirstUnderscore()
    {
        var parsed = ComponentIdParsers.parseLibTokenArg("abcDEF123_7");
        assertTrue(parsed.isPresent());
        assertEquals("abcDEF123", parsed.get().token());
        assertEquals(7, parsed.get().index());
    }

    @Test
    void parseLibTokenArg_negativeIndex_parsesAsASignedDelta()
    {
        var parsed = ComponentIdParsers.parseLibTokenArg("abcDEF123_-1");
        assertTrue(parsed.isPresent());
        assertEquals("abcDEF123", parsed.get().token());
        assertEquals(-1, parsed.get().index());
    }

    @Test
    void parseLibTokenArg_invalid_returnsEmpty()
    {
        assertTrue(ComponentIdParsers.parseLibTokenArg(null).isEmpty());
        assertTrue(ComponentIdParsers.parseLibTokenArg("").isEmpty());
        assertTrue(ComponentIdParsers.parseLibTokenArg("abc_notanumber").isEmpty());
    }
}
