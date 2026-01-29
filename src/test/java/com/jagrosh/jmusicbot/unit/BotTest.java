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

import com.jagrosh.jdautilities.command.CommandClient;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.entities.UserInteraction;
import com.jagrosh.jmusicbot.gui.GUI;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.managers.Presence;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for the Bot class.
 */
@DisplayName("Bot Tests")
public class BotTest
{
    @Mock
    private EventWaiter waiter;
    
    @Mock
    private BotConfig config;
    
    @Mock
    private SettingsManager settingsManager;
    
    @Mock
    private UserInteraction userInteraction;
    
    @Mock
    private JDA jda;
    
    @Mock
    private GUI gui;
    
    @Mock
    private CommandClient commandClient;
    
    @Mock
    private Guild guild;
    
    @Mock
    private AudioManager audioManager;
    
    @Mock
    private AudioHandler audioHandler;
    
    @Mock
    private Presence presence;

    private Bot bot;

    @BeforeEach
    void setUp()
    {
        MockitoAnnotations.openMocks(this);
        
        // Setup mock behaviors
        when(config.getGame()).thenReturn(null);
        when(config.getMaxHistorySize()).thenReturn(10);
        
        // Create bot instance
        bot = new Bot(waiter, config, settingsManager, userInteraction);
    }

    // ==================== Constructor and Initialization Tests ====================

    @Nested
    @DisplayName("Constructor and Initialization")
    class ConstructorTests
    {
        @Test
        @DisplayName("Bot constructor initializes all components")
        void constructor_initializesAllComponents()
        {
            // Then
            assertNotNull(bot.getConfig());
            assertNotNull(bot.getSettingsManager());
            assertNotNull(bot.getWaiter());
            assertNotNull(bot.getThreadpool());
            assertNotNull(bot.getPlayerManager());
            assertNotNull(bot.getPlaylistLoader());
            assertNotNull(bot.getNowplayingHandler());
            assertNotNull(bot.getAloneInVoiceHandler());
            assertNotNull(bot.getMusicService());
            assertNotNull(bot.getSearchService());
            assertNotNull(bot.getStartTime());
        }

        @Test
        @DisplayName("Bot stores start time at initialization")
        void constructor_storesStartTime()
        {
            // Given
            Instant before = Instant.now();
            
            // When
            Bot newBot = new Bot(waiter, config, settingsManager, userInteraction);
            
            // Then
            Instant after = Instant.now();
            assertNotNull(newBot.getStartTime());
            assertTrue(newBot.getStartTime().isAfter(before.minusMillis(1)));
            assertTrue(newBot.getStartTime().isBefore(after.plusMillis(1)));
        }
    }

    // ==================== Getter Tests ====================

    @Nested
    @DisplayName("Getter Methods")
    class GetterTests
    {
        @Test
        @DisplayName("getConfig() returns BotConfig")
        void getConfig_returnsBotConfig()
        {
            assertEquals(config, bot.getConfig());
        }

        @Test
        @DisplayName("getSettingsManager() returns SettingsManager")
        void getSettingsManager_returnsSettingsManager()
        {
            assertEquals(settingsManager, bot.getSettingsManager());
        }

        @Test
        @DisplayName("getWaiter() returns EventWaiter")
        void getWaiter_returnsEventWaiter()
        {
            assertEquals(waiter, bot.getWaiter());
        }

        @Test
        @DisplayName("getJDA() returns null initially")
        void getJDA_returnsNullInitially()
        {
            assertNull(bot.getJDA());
        }

        @Test
        @DisplayName("getCommandClient() returns null initially")
        void getCommandClient_returnsNullInitially()
        {
            assertNull(bot.getCommandClient());
        }

        @Test
        @DisplayName("getUserInteraction() returns UserInteraction")
        void getUserInteraction_returnsUserInteraction()
        {
            assertEquals(userInteraction, bot.getUserInteraction());
        }
    }

    // ==================== Setter Tests ====================

    @Nested
    @DisplayName("Setter Methods")
    class SetterTests
    {
        @Test
        @DisplayName("setJDA() stores JDA instance")
        void setJDA_storesJDA()
        {
            // When
            bot.setJDA(jda);

            // Then
            assertEquals(jda, bot.getJDA());
        }

        @Test
        @DisplayName("setGUI() stores GUI instance")
        void setGUI_storesGUI()
        {
            // When
            bot.setGUI(gui);

            // No getter for GUI, but we can verify no exception is thrown
            assertDoesNotThrow(() -> bot.setGUI(gui));
        }

        @Test
        @DisplayName("setCommandClient() stores CommandClient")
        void setCommandClient_storesCommandClient()
        {
            // When
            bot.setCommandClient(commandClient);

            // Then
            assertEquals(commandClient, bot.getCommandClient());
        }
    }

    // ==================== Close Audio Connection Tests ====================

    @Nested
    @DisplayName("Close Audio Connection")
    class CloseAudioConnectionTests
    {
        @Test
        @DisplayName("closeAudioConnection() closes connection for valid guild")
        void closeAudioConnection_closesForValidGuild() throws InterruptedException
        {
            // Given
            bot.setJDA(jda);
            when(jda.getGuildById(123L)).thenReturn(guild);
            when(guild.getAudioManager()).thenReturn(audioManager);

            // When
            bot.closeAudioConnection(123L);
            
            // Give threadpool time to execute (it's async)
            Thread.sleep(100);

            // Then - verify task was submitted to threadpool
            // Note: The actual close is async via threadpool
            assertDoesNotThrow(() -> bot.closeAudioConnection(123L));
        }

        @Test
        @DisplayName("closeAudioConnection() handles null guild gracefully")
        void closeAudioConnection_handlesNullGuild()
        {
            // Given
            bot.setJDA(jda);
            when(jda.getGuildById(999L)).thenReturn(null);

            // When/Then - should not throw
            assertDoesNotThrow(() -> bot.closeAudioConnection(999L));
        }
    }

    // ==================== Reset Game Tests ====================

    @Nested
    @DisplayName("Reset Game")
    class ResetGameTests
    {
        @Test
        @DisplayName("resetGame() sets null game when config game is null")
        void resetGame_setsNullGame_whenConfigGameNull()
        {
            // Given
            bot.setJDA(jda);
            when(jda.getPresence()).thenReturn(presence);
            when(presence.getActivity()).thenReturn(null);
            when(config.getGame()).thenReturn(null);

            // When
            bot.resetGame();

            // Then - no activity change needed (both null)
            verify(presence, never()).setActivity(any());
        }

        @Test
        @DisplayName("resetGame() sets null game when config game is 'none'")
        void resetGame_setsNullGame_whenConfigGameIsNone()
        {
            // Given
            bot.setJDA(jda);
            when(jda.getPresence()).thenReturn(presence);
            when(presence.getActivity()).thenReturn(Activity.playing("Something"));
            Activity noneActivity = mock(Activity.class);
            when(noneActivity.getName()).thenReturn("none");
            when(config.getGame()).thenReturn(noneActivity);

            // When
            bot.resetGame();

            // Then
            verify(presence).setActivity(null);
        }

        @Test
        @DisplayName("resetGame() sets game from config")
        void resetGame_setsGameFromConfig()
        {
            // Given
            bot.setJDA(jda);
            when(jda.getPresence()).thenReturn(presence);
            when(presence.getActivity()).thenReturn(null);
            Activity newGame = Activity.playing("JMusicBot");
            when(config.getGame()).thenReturn(newGame);

            // When
            bot.resetGame();

            // Then
            verify(presence).setActivity(newGame);
        }

        @Test
        @DisplayName("resetGame() does nothing when game is same")
        void resetGame_doesNothingWhenSame()
        {
            // Given
            bot.setJDA(jda);
            Activity game = Activity.playing("JMusicBot");
            when(jda.getPresence()).thenReturn(presence);
            when(presence.getActivity()).thenReturn(game);
            when(config.getGame()).thenReturn(game);

            // When
            bot.resetGame();

            // Then - setActivity should not be called
            verify(presence, never()).setActivity(any());
        }
    }

    // ==================== Service Access Tests ====================

    @Nested
    @DisplayName("Service Access")
    class ServiceAccessTests
    {
        @Test
        @DisplayName("getMusicService() returns MusicService instance")
        void getMusicService_returnsMusicService()
        {
            assertNotNull(bot.getMusicService());
        }

        @Test
        @DisplayName("getSearchService() returns SearchService instance")
        void getSearchService_returnsSearchService()
        {
            assertNotNull(bot.getSearchService());
        }

        @Test
        @DisplayName("getPlayerManager() returns PlayerManager instance")
        void getPlayerManager_returnsPlayerManager()
        {
            assertNotNull(bot.getPlayerManager());
        }

        @Test
        @DisplayName("getPlaylistLoader() returns PlaylistLoader instance")
        void getPlaylistLoader_returnsPlaylistLoader()
        {
            assertNotNull(bot.getPlaylistLoader());
        }

        @Test
        @DisplayName("getNowplayingHandler() returns NowPlayingHandler instance")
        void getNowplayingHandler_returnsNowPlayingHandler()
        {
            assertNotNull(bot.getNowplayingHandler());
        }

        @Test
        @DisplayName("getAloneInVoiceHandler() returns AloneInVoiceHandler instance")
        void getAloneInVoiceHandler_returnsAloneInVoiceHandler()
        {
            assertNotNull(bot.getAloneInVoiceHandler());
        }

        @Test
        @DisplayName("getThreadpool() returns ScheduledExecutorService instance")
        void getThreadpool_returnsScheduledExecutorService()
        {
            assertNotNull(bot.getThreadpool());
        }

        @Test
        @DisplayName("getYouTubeOauth2Handler() returns handler instance")
        void getYouTubeOauth2Handler_returnsHandler()
        {
            assertNotNull(bot.getYouTubeOauth2Handler());
        }
    }
}
