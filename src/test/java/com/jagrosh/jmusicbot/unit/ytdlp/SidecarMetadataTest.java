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
package com.jagrosh.jmusicbot.unit.ytdlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagrosh.jmusicbot.ytdlp.SidecarMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@DisplayName("SidecarMetadata Tests")
class SidecarMetadataTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static JsonNode json(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    @DisplayName("artist comes from artist")
    void artist_fromArtistField() {
        SidecarMetadata m = SidecarMetadata.parse(json("{ \"artist\": \"Crazy Frog\" }"));

        assertEquals("Crazy Frog", m.artist());
    }

    @Test
    @DisplayName("artist falls back to artists[0]")
    void artist_fallsBackToArtistsArray() {
        SidecarMetadata m = SidecarMetadata.parse(json("{ \"artists\": [\"Crazy Frog\", \"Someone Else\"] }"));

        assertEquals("Crazy Frog", m.artist());
    }

    @Test
    @DisplayName("artist falls back to creator when there is no artist or artists[0]")
    void artist_fallsBackToCreator() {
        SidecarMetadata m = SidecarMetadata.parse(json("{ \"creator\": \"Crazy Frog\" }"));

        assertEquals("Crazy Frog", m.artist());
    }

    @Test
    @DisplayName("release_year as a number")
    void releaseYear_number() {
        SidecarMetadata m = SidecarMetadata.parse(json("{ \"release_year\": 2009 }"));

        assertEquals(2009, m.releaseYear());
    }

    @Test
    @DisplayName("release_year as a string")
    void releaseYear_string() {
        SidecarMetadata m = SidecarMetadata.parse(json("{ \"release_year\": \"2009\" }"));

        assertEquals(2009, m.releaseYear());
    }

    @Test
    @DisplayName("release_date as yyyymmdd gives the year")
    void releaseDate_yyyymmdd() {
        SidecarMetadata m = SidecarMetadata.parse(json("{ \"release_date\": \"20090101\" }"));

        assertEquals(2009, m.releaseYear());
    }

    @Test
    @DisplayName("release_date as a plain year string also works")
    void releaseDate_plainYear() {
        SidecarMetadata m = SidecarMetadata.parse(json("{ \"release_date\": \"2009\" }"));

        assertEquals(2009, m.releaseYear());
    }

    @Test
    @DisplayName("an unparseable release_date gives a null year")
    void releaseDate_garbage_givesNullYear() {
        SidecarMetadata m = SidecarMetadata.parse(json("{ \"release_date\": \"garbage\" }"));

        assertNull(m.releaseYear());
    }

    @Test
    @DisplayName("a blank isrc gives null")
    void isrc_blank_givesNull() {
        SidecarMetadata m = SidecarMetadata.parse(json("{ \"isrc\": \"   \" }"));

        assertNull(m.isrc());
    }

    @Test
    @DisplayName("duration 172.4 becomes 172400ms")
    void duration_roundsToMillis() {
        SidecarMetadata m = SidecarMetadata.parse(json("{ \"duration\": 172.4 }"));

        assertEquals(172_400L, m.durationMs());
    }

    @Test
    @DisplayName("an empty object gives all nulls and a duration of 0")
    void emptyObject_allNulls() {
        SidecarMetadata m = SidecarMetadata.parse(json("{}"));

        assertNull(m.artist());
        assertNull(m.track());
        assertNull(m.album());
        assertNull(m.releaseYear());
        assertNull(m.isrc());
        assertEquals(0L, m.durationMs());
    }

    @Test
    @DisplayName("parse() never throws, even for null or non-object input")
    void parse_neverThrows() {
        assertEquals(SidecarMetadata.parse(null), SidecarMetadata.parse(null));
        SidecarMetadata m = SidecarMetadata.parse(json("[1, 2, 3]"));
        assertNull(m.artist());
    }

    @Test
    @DisplayName("track and album come straight from their fields")
    void trackAndAlbum_plainFields() {
        SidecarMetadata m = SidecarMetadata.parse(json("{ \"track\": \"Axel F\", \"album\": \"Crazy Hits\" }"));

        assertEquals("Axel F", m.track());
        assertEquals("Crazy Hits", m.album());
    }
}
