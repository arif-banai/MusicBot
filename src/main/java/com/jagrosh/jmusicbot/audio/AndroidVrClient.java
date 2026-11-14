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

import com.sedmelluq.discord.lavaplayer.tools.io.HttpInterface;
import dev.lavalink.youtube.clients.AndroidVr;
import dev.lavalink.youtube.clients.AndroidVrWithThumbnail;
import dev.lavalink.youtube.clients.ClientConfig;
import dev.lavalink.youtube.clients.ClientOptions;
import org.jetbrains.annotations.NotNull;

/**
 * youtube-source's ANDROID_VR client with a newer client version.
 *
 * <p>youtube-source sends ANDROID_VR clientVersion {@code 1.60.19}, which YouTube now always
 * answers with "Sign in to confirm you're not a bot"; {@code 1.65.10} plays with the same
 * request otherwise unchanged (lavalink-devs/youtube-source#255). This client sends whichever of
 * {@link #MIN_CLIENT_VERSION} and youtube-source's own version is newer, so it stops doing
 * anything once upstream catches up and can then be deleted.
 *
 * <p>The config is built fresh on every call instead of copied: {@link ClientConfig#copy()} is
 * shallow, so a field set on a copy (the client version here, visitor data in youtube-source)
 * would leak into the shared base config.
 */
public class AndroidVrClient extends AndroidVrWithThumbnail
{
    public static final String MIN_CLIENT_VERSION = "1.65.10";

    private static final String CLIENT_VERSION = newer(AndroidVr.CLIENT_VERSION, MIN_CLIENT_VERSION);

    public AndroidVrClient()
    {
        super();
    }

    public AndroidVrClient(@NotNull ClientOptions options)
    {
        super(options);
    }

    @Override
    @NotNull
    protected ClientConfig getBaseClientConfig(@NotNull HttpInterface httpInterface)
    {
        return buildConfig(CLIENT_VERSION);
    }

    /** Mirrors {@link AndroidVr#BASE_CONFIG}, with {@code clientVersion} in both the body and User-Agent. */
    private static ClientConfig buildConfig(String clientVersion)
    {
        return new ClientConfig()
            .withUserAgent(String.format(
                "com.google.android.apps.youtube.vr.oculus/%s (Linux; U; Android %s; eureka-user Build/SQ3A.220605.009.A1) gzip",
                clientVersion, AndroidVr.ANDROID_VERSION.getOsVersion()))
            .withClientName("ANDROID_VR")
            .withClientField("clientVersion", clientVersion)
            .withClientField("androidSdkVersion", AndroidVr.ANDROID_VERSION.getSdkVersion());
    }

    /** @return the newer of two dotted numeric versions */
    public static String newer(String a, String b)
    {
        return compareVersions(a, b) >= 0 ? a : b;
    }

    /** Compares dotted numeric versions ("1.65.10" > "1.65.9"); missing parts count as 0. */
    public static int compareVersions(String a, String b)
    {
        String[] pa = a.split("\\.");
        String[] pb = b.split("\\.");
        for (int i = 0; i < Math.max(pa.length, pb.length); i++)
        {
            int x = i < pa.length ? Integer.parseInt(pa[i]) : 0;
            int y = i < pb.length ? Integer.parseInt(pb[i]) : 0;
            if (x != y)
            {
                return Integer.compare(x, y);
            }
        }
        return 0;
    }
}
