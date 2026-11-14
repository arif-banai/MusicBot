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
package com.jagrosh.jmusicbot.unit.audio;

import com.jagrosh.jmusicbot.audio.VisionOsClient;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.ClientConfig;
import dev.lavalink.youtube.clients.ClientOptions;
import dev.lavalink.youtube.clients.skeleton.Client;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("VisionOsClient")
class VisionOsClientTest {

    @Test
    @DisplayName("identifies as VISIONOS 1.02 with the visionOS device fields and Safari User-Agent")
    void sendsVisionOsIdentity() throws Exception {
        ClientConfig config = baseConfig(new VisionOsClient());
        Map<String, Object> client = clientFields(config);

        assertEquals("VISIONOS", config.getName());
        assertEquals("VISIONOS", client.get("clientName"));
        assertEquals("1.02", client.get("clientVersion"));
        assertEquals("Apple", client.get("deviceMake"));
        assertEquals("RealityDevice17,1", client.get("deviceModel"));
        assertEquals("visionOS", client.get("osName"));
        assertEquals("26.5.23O471", client.get("osVersion"));
        assertTrue(config.getUserAgent().contains("Safari/605.1.15"), config.getUserAgent());
    }

    @Test
    @DisplayName("sends no player params and needs no player script (its URLs carry no n or signature)")
    void noParamsNoPlayerScript() {
        VisionOsClient client = new VisionOsClient();
        assertNull(client.getPlayerParams());
        assertFalse(client.requirePlayerScript());
        assertFalse(client.supportsOAuth());
    }

    @Test
    @DisplayName("is playback-only: never handles loads, search, playlists or mixes")
    void playbackOnly() {
        VisionOsClient client = new VisionOsClient();
        assertFalse(client.canHandleRequest("https://www.youtube.com/watch?v=k85mRPqvMbE"));
        assertFalse(client.canHandleRequest("ytsearch:crazy frog"));
        assertTrue(client.getOptions().getPlayback());
        assertThrows(FriendlyException.class, () -> client.loadVideo(null, null, "k85mRPqvMbE"));
        assertThrows(FriendlyException.class, () -> client.loadSearch(null, null, "crazy frog"));
        assertThrows(FriendlyException.class, () -> client.loadPlaylist(null, null, "PL1", null));
        assertThrows(FriendlyException.class, () -> client.loadMix(null, null, "RD1", null));
    }

    @Test
    @DisplayName("honours client options")
    void honoursOptions() {
        ClientOptions noPlayback = new ClientOptions();
        noPlayback.setPlayback(false);
        assertFalse(new VisionOsClient(noPlayback).getOptions().getPlayback());
    }

    @Test
    @DisplayName("changes to one request's config never leak into the next")
    void configsAreIndependent() throws Exception {
        VisionOsClient client = new VisionOsClient();
        clientFields(baseConfig(client)).put("clientVersion", "0.0.1");
        assertEquals("1.02", clientFields(baseConfig(client)).get("clientVersion"));
    }

    @Test
    @DisplayName("reports VISIONOS as its identifier (used in failure messages)")
    void identifier() {
        Client client = new VisionOsClient();
        assertEquals("VISIONOS", client.getIdentifier());
    }

    private static ClientConfig baseConfig(VisionOsClient client) throws Exception {
        Method m = VisionOsClient.class.getDeclaredMethod("getBaseClientConfig", HttpInterface.class);
        m.setAccessible(true);
        return (ClientConfig) m.invoke(client, (Object) null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> clientFields(ClientConfig config) {
        Map<String, Object> context = (Map<String, Object>) config.getRoot().get("context");
        return (Map<String, Object>) context.get("client");
    }
}
