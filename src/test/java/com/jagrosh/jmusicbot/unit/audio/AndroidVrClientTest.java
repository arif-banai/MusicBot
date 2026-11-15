/*
 * Copyright 2026 Arif Banai (arif-banai)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.unit.audio;

import com.jagrosh.jmusicbot.audio.AndroidVrClient;
import dev.lavalink.youtube.clients.AndroidVr;
import dev.lavalink.youtube.clients.AndroidVrWithThumbnail;
import dev.lavalink.youtube.clients.ClientConfig;
import dev.lavalink.youtube.clients.ClientOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AndroidVrClient")
class AndroidVrClientTest {

    @Test
    @DisplayName("sends at least ANDROID_VR client version 1.65.10 (1.60.19 is bot-checked)")
    void sendsUpdatedClientVersion() throws Exception {
        ClientConfig config = baseConfig(new AndroidVrClient());

        assertTrue(AndroidVrClient.compareVersions(clientVersion(config), "1.65.10") >= 0,
            "clientVersion must be at least 1.65.10, was " + clientVersion(config));
        assertTrue(config.getUserAgent().contains("/" + clientVersion(config) + " "),
            "User-Agent should carry the same version as clientVersion: " + config.getUserAgent());
        assertEquals("ANDROID_VR", config.getName());
    }

    @Test
    @DisplayName("changes to one request's config leak into neither ours nor youtube-source's")
    void configsAreIndependent() throws Exception {
        AndroidVrClient client = new AndroidVrClient();
        String upstreamBefore = clientVersion(AndroidVr.BASE_CONFIG);
        String oursBefore = clientVersion(baseConfig(client));

        baseConfig(client).withClientField("clientVersion", "0.0.1");

        assertEquals(upstreamBefore, clientVersion(AndroidVr.BASE_CONFIG));
        assertEquals(oursBefore, clientVersion(baseConfig(client)));
    }

    @Test
    @DisplayName("is still an AndroidVrWithThumbnail and honours client options")
    void keepsUpstreamBehaviour() {
        ClientOptions noPlayback = new ClientOptions();
        noPlayback.setPlayback(false);

        AndroidVrClient client = new AndroidVrClient(noPlayback);

        assertInstanceOf(AndroidVrWithThumbnail.class, client);
        assertFalse(client.getOptions().getPlayback());
        assertEquals("ANDROID_VR", client.getIdentifier());
    }

    @Test
    @DisplayName("prefers whichever of the upstream and minimum versions is newer")
    void picksNewerVersion() {
        assertEquals("1.65.10", AndroidVrClient.newer("1.60.19", "1.65.10"));
        assertEquals("1.70.2", AndroidVrClient.newer("1.70.2", "1.65.10"));
        assertEquals("1.65.10", AndroidVrClient.newer("1.65.9", "1.65.10"));
        assertEquals("1.65.10", AndroidVrClient.newer("1.65.10", "1.65.10"));
    }

    private static ClientConfig baseConfig(AndroidVrClient client) throws Exception {
        Method m = AndroidVr.class.getDeclaredMethod("getBaseClientConfig",
            com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface.class);
        m.setAccessible(true);
        return (ClientConfig) m.invoke(client, (Object) null);
    }

    @SuppressWarnings("unchecked")
    private static String clientVersion(ClientConfig config) {
        Map<String, Object> context = (Map<String, Object>) config.getRoot().get("context");
        Map<String, Object> client = (Map<String, Object>) context.get("client");
        return (String) client.get("clientVersion");
    }
}
