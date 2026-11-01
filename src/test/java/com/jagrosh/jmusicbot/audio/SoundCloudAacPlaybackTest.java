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
package com.jagrosh.jmusicbot.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioItem;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Loads a public SoundCloud track that is served as unencrypted AAC HLS.
 * Set SOUNDCLOUD_LIVE=true to run it.
 */
class SoundCloudAacPlaybackTest
{
    @Test
    @EnabledIfEnvironmentVariable(named = "SOUNDCLOUD_LIVE", matches = "true")
    void playsUnencryptedAacTrack() throws Exception
    {
        DefaultAudioPlayerManager manager = new DefaultAudioPlayerManager();
        manager.registerSourceManager(SoundCloudPlaybackSourceManager.create());
        AudioPlayer player = manager.createPlayer();
        AtomicReference<FriendlyException> playbackError = new AtomicReference<>();
        player.addListener(new AudioEventAdapter()
        {
            @Override
            public void onTrackException(AudioPlayer audioPlayer, AudioTrack track, FriendlyException exception)
            {
                playbackError.set(exception);
            }
        });

        try
        {
            AudioItem item = manager.loadItemSync("https://soundcloud.com/forss/flickermood");
            assertTrue(item instanceof AudioTrack, "expected a track, got " + item);
            AudioTrack track = (AudioTrack) item;
            assertTrue(track.getIdentifier().startsWith("A:"), track.getIdentifier());

            player.playTrack(track);
            long timecode = 0;
            long deadline = System.currentTimeMillis() + 15000;
            while (timecode < 1000 && playbackError.get() == null && System.currentTimeMillis() < deadline)
            {
                AudioFrame frame = player.provide(1, TimeUnit.SECONDS);
                if (frame != null && !frame.isTerminator())
                {
                    timecode = frame.getTimecode();
                }
            }

            assertNull(playbackError.get(), playbackError.get() == null ? "" : playbackError.get().getMessage());
            assertTrue(timecode >= 1000, "timecode=" + timecode);
        }
        finally
        {
            player.destroy();
            manager.shutdown();
        }
    }
}
