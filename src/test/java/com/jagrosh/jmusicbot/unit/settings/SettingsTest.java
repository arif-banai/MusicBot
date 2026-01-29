package com.jagrosh.jmusicbot.unit.settings;

import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.RepeatMode;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class SettingsTest {

    private SettingsManager manager;
    private Settings settings;

    @BeforeEach
    public void setUp() {
        // Mock manager to prevent NPE when setters call writeSettings()
        manager = mock(SettingsManager.class);
        settings = new Settings(manager, 0, 0, 0, 100, null, RepeatMode.OFF, null, -1, QueueType.FAIR);
    }

    @Test
    public void testDefaultValues() {
        assertEquals(100, settings.getVolume());
        assertEquals(RepeatMode.OFF, settings.getRepeatMode());
        assertEquals(QueueType.FAIR, settings.getQueueType());
        assertNull(settings.getPrefix());
        assertNull(settings.getDefaultPlaylist());
        assertEquals(-1, settings.getSkipRatio());
        assertTrue(settings.getPrefixes().isEmpty());
    }

    @Test
    public void testSetVolume() {
        settings.setVolume(50);
        assertEquals(50, settings.getVolume());

        settings.setVolume(0);
        assertEquals(0, settings.getVolume());

        settings.setVolume(150);
        assertEquals(150, settings.getVolume());
    }

    @Test
    public void testSetRepeatMode() {
        settings.setRepeatMode(RepeatMode.ALL);
        assertEquals(RepeatMode.ALL, settings.getRepeatMode());

        settings.setRepeatMode(RepeatMode.SINGLE);
        assertEquals(RepeatMode.SINGLE, settings.getRepeatMode());

        settings.setRepeatMode(RepeatMode.OFF);
        assertEquals(RepeatMode.OFF, settings.getRepeatMode());
    }

    @Test
    public void testSetQueueType() {
        settings.setQueueType(QueueType.LINEAR);
        assertEquals(QueueType.LINEAR, settings.getQueueType());

        settings.setQueueType(QueueType.FAIR);
        assertEquals(QueueType.FAIR, settings.getQueueType());
    }

    @Test
    public void testSetPrefix() {
        settings.setPrefix("!");
        assertEquals("!", settings.getPrefix());
        assertTrue(settings.getPrefixes().contains("!"));
        assertEquals(1, settings.getPrefixes().size());

        settings.setPrefix("!!");
        assertEquals("!!", settings.getPrefix());
        assertTrue(settings.getPrefixes().contains("!!"));
    }

    @Test
    public void testSetPrefixNull() {
        settings.setPrefix("!");
        settings.setPrefix(null);
        assertNull(settings.getPrefix());
        assertTrue(settings.getPrefixes().isEmpty());
    }

    @Test
    public void testSetDefaultPlaylist() {
        settings.setDefaultPlaylist("my_playlist");
        assertEquals("my_playlist", settings.getDefaultPlaylist());

        settings.setDefaultPlaylist(null);
        assertNull(settings.getDefaultPlaylist());
    }

    @Test
    public void testSetSkipRatio() {
        settings.setSkipRatio(0.5);
        assertEquals(0.5, settings.getSkipRatio());

        settings.setSkipRatio(0.0);
        assertEquals(0.0, settings.getSkipRatio());

        settings.setSkipRatio(1.0);
        assertEquals(1.0, settings.getSkipRatio());
    }

    @Test
    public void testConstructorWithStringIds() {
        Settings s = new Settings(manager, "123", "456", "789", 75, "playlist", RepeatMode.ALL, "?", 0.6, QueueType.LINEAR);
        
        assertEquals(75, s.getVolume());
        assertEquals("playlist", s.getDefaultPlaylist());
        assertEquals(RepeatMode.ALL, s.getRepeatMode());
        assertEquals("?", s.getPrefix());
        assertEquals(0.6, s.getSkipRatio());
        assertEquals(QueueType.LINEAR, s.getQueueType());
    }

    @Test
    public void testConstructorWithInvalidStringIds() {
        // Invalid IDs should default to 0 without throwing
        Settings s = new Settings(manager, "invalid", "also_invalid", "nope", 100, null, RepeatMode.OFF, null, -1, QueueType.FAIR);
        
        assertEquals(100, s.getVolume());
        assertEquals(RepeatMode.OFF, s.getRepeatMode());
    }
}
