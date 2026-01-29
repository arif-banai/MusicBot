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
package com.jagrosh.jmusicbot.testutil.service;

import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.queue.AbstractQueue;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;

import java.util.ArrayList;
import java.util.List;

import static com.jagrosh.jmusicbot.testutil.TestConstants.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Builder for constructing queue states for testing.
 * Provides fine-grained control over queue contents.
 * 
 * Usage:
 * <pre>
 * AbstractQueue<QueuedTrack> queue = QueueStateBuilder.create()
 *     .addTrack("Song 1", "Artist 1", 180000)
 *     .addTrack("Song 2", "Artist 2", 240000)
 *     .build();
 * </pre>
 */
public class QueueStateBuilder
{
    private final List<QueuedTrack> tracks = new ArrayList<>();
    private final List<QueuedTrack> history = new ArrayList<>();
    private long defaultUserId = USER_ID;
    private long defaultChannelId = CHANNEL_ID;

    private QueueStateBuilder()
    {
    }

    /**
     * Creates a new queue state builder.
     */
    public static QueueStateBuilder create()
    {
        return new QueueStateBuilder();
    }

    /**
     * Sets the default user ID for new tracks.
     */
    public QueueStateBuilder withDefaultUser(long userId)
    {
        this.defaultUserId = userId;
        return this;
    }

    /**
     * Sets the default channel ID for new tracks.
     */
    public QueueStateBuilder withDefaultChannel(long channelId)
    {
        this.defaultChannelId = channelId;
        return this;
    }

    /**
     * Adds a track with the given properties.
     */
    public QueueStateBuilder addTrack(String title, String author, long durationMs)
    {
        return addTrack(title, author, durationMs, defaultUserId);
    }

    /**
     * Adds a track with the given properties and specific user.
     */
    public QueueStateBuilder addTrack(String title, String author, long durationMs, long userId)
    {
        QueuedTrack qt = createMockQueuedTrack(title, author, durationMs, userId);
        tracks.add(qt);
        return this;
    }

    /**
     * Adds multiple tracks with default properties.
     */
    public QueueStateBuilder addTracks(int count)
    {
        for (int i = 0; i < count; i++)
        {
            addTrack("Track " + (tracks.size() + 1), "Artist", 180000);
        }
        return this;
    }

    /**
     * Adds a track to the history.
     */
    public QueueStateBuilder addToHistory(String title, String author, long durationMs)
    {
        QueuedTrack qt = createMockQueuedTrack(title, author, durationMs, defaultUserId);
        history.add(qt);
        return this;
    }

    /**
     * Adds multiple tracks to history.
     */
    public QueueStateBuilder addToHistory(int count)
    {
        for (int i = 0; i < count; i++)
        {
            addToHistory("Previous Track " + (history.size() + 1), "Artist", 180000);
        }
        return this;
    }

    private QueuedTrack createMockQueuedTrack(String title, String author, long durationMs, long userId)
    {
        QueuedTrack qt = mock(QueuedTrack.class);
        AudioTrack track = mock(AudioTrack.class);
        AudioTrackInfo info = new AudioTrackInfo(title, author, durationMs, 
                "id-" + tracks.size(), false, "https://example.com/" + tracks.size());
        
        when(track.getInfo()).thenReturn(info);
        when(track.getDuration()).thenReturn(durationMs);
        when(qt.getTrack()).thenReturn(track);
        when(qt.getIdentifier()).thenReturn(userId);
        
        // Create request metadata
        RequestMetadata metadata = mock(RequestMetadata.class);
        when(metadata.getOwner()).thenReturn(userId);
        when(track.getUserData(RequestMetadata.class)).thenReturn(metadata);
        
        return qt;
    }

    /**
     * Builds and returns a mock AbstractQueue with the configured state.
     */
    @SuppressWarnings("unchecked")
    public AbstractQueue<QueuedTrack> build()
    {
        AbstractQueue<QueuedTrack> queue = mock(AbstractQueue.class);
        
        when(queue.size()).thenReturn(tracks.size());
        when(queue.isEmpty()).thenReturn(tracks.isEmpty());
        when(queue.getList()).thenReturn(new ArrayList<>(tracks));
        
        if (!tracks.isEmpty())
        {
            when(queue.get(anyInt())).thenAnswer(inv -> {
                int index = inv.getArgument(0);
                return index >= 0 && index < tracks.size() ? tracks.get(index) : null;
            });
        }

        // History mock
        com.jagrosh.jmusicbot.queue.HistoryQueue<QueuedTrack> historyMock = mock(com.jagrosh.jmusicbot.queue.HistoryQueue.class);
        when(historyMock.getList()).thenReturn(new ArrayList<>(history));
        when(historyMock.isEmpty()).thenReturn(history.isEmpty());
        when(historyMock.size()).thenReturn(history.size());
        when(queue.getHistory()).thenReturn(historyMock);
        
        return queue;
    }

    /**
     * Applies this queue state to a fixture.
     */
    public void applyTo(ServiceTestFixture fixture)
    {
        AbstractQueue<QueuedTrack> builtQueue = build();
        when(fixture.getAudioHandler().getQueue()).thenReturn(builtQueue);
    }

    /**
     * Returns the list of tracks for direct access.
     */
    public List<QueuedTrack> getTracks()
    {
        return new ArrayList<>(tracks);
    }

    /**
     * Returns the history list for direct access.
     */
    public List<QueuedTrack> getHistory()
    {
        return new ArrayList<>(history);
    }
}
