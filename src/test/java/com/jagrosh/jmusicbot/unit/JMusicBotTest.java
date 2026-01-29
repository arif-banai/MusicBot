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
package com.jagrosh.jmusicbot.unit;

import com.jagrosh.jmusicbot.JMusicBot;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the JMusicBot class.
 * 
 * Note: The main() and startBot() methods are difficult to test as they
 * involve JDA initialization, file I/O, and System.exit(). These tests
 * focus on the testable static constants and configurations.
 */
@DisplayName("JMusicBot Tests")
public class JMusicBotTest
{
    // ==================== Recommended Permissions Tests ====================

    @Nested
    @DisplayName("Recommended Permissions")
    class RecommendedPermissionsTests
    {
        @Test
        @DisplayName("RECOMMENDED_PERMS contains required permissions")
        void recommendedPerms_containsRequiredPermissions()
        {
            Set<Permission> perms = Arrays.stream(JMusicBot.RECOMMENDED_PERMS).collect(Collectors.toSet());

            // Core permissions
            assertTrue(perms.contains(Permission.VIEW_CHANNEL), "Should have VIEW_CHANNEL");
            assertTrue(perms.contains(Permission.MESSAGE_SEND), "Should have MESSAGE_SEND");
            assertTrue(perms.contains(Permission.MESSAGE_HISTORY), "Should have MESSAGE_HISTORY");
        }

        @Test
        @DisplayName("RECOMMENDED_PERMS contains voice permissions")
        void recommendedPerms_containsVoicePermissions()
        {
            Set<Permission> perms = Arrays.stream(JMusicBot.RECOMMENDED_PERMS).collect(Collectors.toSet());

            assertTrue(perms.contains(Permission.VOICE_CONNECT), "Should have VOICE_CONNECT");
            assertTrue(perms.contains(Permission.VOICE_SPEAK), "Should have VOICE_SPEAK");
        }

        @Test
        @DisplayName("RECOMMENDED_PERMS contains message interaction permissions")
        void recommendedPerms_containsMessageInteractionPermissions()
        {
            Set<Permission> perms = Arrays.stream(JMusicBot.RECOMMENDED_PERMS).collect(Collectors.toSet());

            assertTrue(perms.contains(Permission.MESSAGE_ADD_REACTION), "Should have MESSAGE_ADD_REACTION");
            assertTrue(perms.contains(Permission.MESSAGE_EMBED_LINKS), "Should have MESSAGE_EMBED_LINKS");
            assertTrue(perms.contains(Permission.MESSAGE_ATTACH_FILES), "Should have MESSAGE_ATTACH_FILES");
            assertTrue(perms.contains(Permission.MESSAGE_MANAGE), "Should have MESSAGE_MANAGE");
            assertTrue(perms.contains(Permission.MESSAGE_EXT_EMOJI), "Should have MESSAGE_EXT_EMOJI");
        }

        @Test
        @DisplayName("RECOMMENDED_PERMS contains nickname change permission")
        void recommendedPerms_containsNicknamePermission()
        {
            Set<Permission> perms = Arrays.stream(JMusicBot.RECOMMENDED_PERMS).collect(Collectors.toSet());

            assertTrue(perms.contains(Permission.NICKNAME_CHANGE), "Should have NICKNAME_CHANGE");
        }

        @Test
        @DisplayName("RECOMMENDED_PERMS has correct count")
        void recommendedPerms_hasCorrectCount()
        {
            // 11 permissions total
            assertEquals(11, JMusicBot.RECOMMENDED_PERMS.length);
        }
    }

    // ==================== Gateway Intents Tests ====================

    @Nested
    @DisplayName("Gateway Intents")
    class GatewayIntentsTests
    {
        @Test
        @DisplayName("INTENTS contains DIRECT_MESSAGES")
        void intents_containsDirectMessages()
        {
            Set<GatewayIntent> intents = Arrays.stream(JMusicBot.INTENTS).collect(Collectors.toSet());
            assertTrue(intents.contains(GatewayIntent.DIRECT_MESSAGES));
        }

        @Test
        @DisplayName("INTENTS contains GUILD_MESSAGES")
        void intents_containsGuildMessages()
        {
            Set<GatewayIntent> intents = Arrays.stream(JMusicBot.INTENTS).collect(Collectors.toSet());
            assertTrue(intents.contains(GatewayIntent.GUILD_MESSAGES));
        }

        @Test
        @DisplayName("INTENTS contains GUILD_MESSAGE_REACTIONS")
        void intents_containsGuildMessageReactions()
        {
            Set<GatewayIntent> intents = Arrays.stream(JMusicBot.INTENTS).collect(Collectors.toSet());
            assertTrue(intents.contains(GatewayIntent.GUILD_MESSAGE_REACTIONS));
        }

        @Test
        @DisplayName("INTENTS contains GUILD_VOICE_STATES")
        void intents_containsGuildVoiceStates()
        {
            Set<GatewayIntent> intents = Arrays.stream(JMusicBot.INTENTS).collect(Collectors.toSet());
            assertTrue(intents.contains(GatewayIntent.GUILD_VOICE_STATES));
        }

        @Test
        @DisplayName("INTENTS contains MESSAGE_CONTENT")
        void intents_containsMessageContent()
        {
            Set<GatewayIntent> intents = Arrays.stream(JMusicBot.INTENTS).collect(Collectors.toSet());
            assertTrue(intents.contains(GatewayIntent.MESSAGE_CONTENT));
        }

        @Test
        @DisplayName("INTENTS has correct count")
        void intents_hasCorrectCount()
        {
            // 5 intents total
            assertEquals(5, JMusicBot.INTENTS.length);
        }
    }

    // ==================== Logger Tests ====================

    @Nested
    @DisplayName("Logger")
    class LoggerTests
    {
        @Test
        @DisplayName("LOG is not null")
        void log_isNotNull()
        {
            assertNotNull(JMusicBot.LOG);
        }

        @Test
        @DisplayName("LOG has correct name")
        void log_hasCorrectName()
        {
            assertEquals(JMusicBot.class.getName(), JMusicBot.LOG.getName());
        }
    }

    // ==================== Static Constants Validation ====================

    @Nested
    @DisplayName("Static Constants Validation")
    class StaticConstantsTests
    {
        @Test
        @DisplayName("RECOMMENDED_PERMS is not empty")
        void recommendedPerms_isNotEmpty()
        {
            assertTrue(JMusicBot.RECOMMENDED_PERMS.length > 0);
        }

        @Test
        @DisplayName("INTENTS is not empty")
        void intents_isNotEmpty()
        {
            assertTrue(JMusicBot.INTENTS.length > 0);
        }

        @Test
        @DisplayName("Permissions are all non-null")
        void permissions_allNonNull()
        {
            for (Permission perm : JMusicBot.RECOMMENDED_PERMS)
            {
                assertNotNull(perm, "All permissions should be non-null");
            }
        }

        @Test
        @DisplayName("Intents are all non-null")
        void intents_allNonNull()
        {
            for (GatewayIntent intent : JMusicBot.INTENTS)
            {
                assertNotNull(intent, "All intents should be non-null");
            }
        }
    }
}
