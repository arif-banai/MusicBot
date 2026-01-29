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
package com.jagrosh.jmusicbot.testutil;

/**
 * Shared constants for test fixtures.
 * All test fixtures should reference these constants to ensure consistency
 * across the test suite.
 */
public final class TestConstants
{
    private TestConstants()
    {
        // Utility class - prevent instantiation
    }

    // ==================== Discord Entity IDs ====================

    /**
     * Standard test guild ID used across all fixtures.
     */
    public static final long GUILD_ID = 123456789L;

    /**
     * Standard test user ID (for the member being tested).
     */
    public static final long USER_ID = 987654321L;

    /**
     * Standard bot owner ID.
     */
    public static final long OWNER_ID = 111111111L;

    /**
     * Standard DJ role ID.
     */
    public static final long DJ_ROLE_ID = 222222222L;

    /**
     * Standard message ID for NP messages and similar.
     */
    public static final long MESSAGE_ID = 444444444L;

    /**
     * Standard text channel ID.
     */
    public static final long CHANNEL_ID = 555555555L;

    /**
     * Standard voice channel ID.
     */
    public static final long VOICE_CHANNEL_ID = 666666666L;

    // ==================== Default Track Properties ====================

    /**
     * Default track title for mock tracks.
     */
    public static final String DEFAULT_TRACK_TITLE = "Test Track";

    /**
     * Default track author for mock tracks.
     */
    public static final String DEFAULT_TRACK_AUTHOR = "Test Author";

    /**
     * Default track duration in milliseconds (3 minutes).
     */
    public static final long DEFAULT_TRACK_DURATION_MS = 180000L;

    /**
     * Default test user name.
     */
    public static final String DEFAULT_USER_NAME = "TestUser";
}
