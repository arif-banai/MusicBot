/*
 * Copyright 2025 Arif Banai <a.banai@gmail.com>.
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
package com.jagrosh.jmusicbot.unit.utils;

import com.jagrosh.jmusicbot.utils.OtherUtil;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class OtherUtilTest
{
    private MockWebServer mockWebServer;

    @BeforeEach
    void setUp() throws IOException
    {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
    }

    @AfterEach
    void tearDown() throws IOException
    {
        mockWebServer.shutdown();
    }

    @ParameterizedTest(name = "{index}: isNewerVersion({0}, {1}) should be {2} - {3}")
    @MethodSource("testData")
    public void testIsNewerVersion(String current, String latest, boolean expected, String reason)
    {
        assertEquals(expected, OtherUtil.isNewerVersion(current, latest), reason);
    }

    private static Stream<Arguments> testData()
    {
        return Stream.of(
                // Newer versions
                Arguments.of("0.5.1", "1.0.0", true, "Latest is newer (major)"),
                Arguments.of("0.5.1", "0.6.0", true, "Latest is newer (minor)"),
                Arguments.of("0.5.1", "0.5.2", true, "Latest is newer (patch)"),

                // Equal versions
                Arguments.of("0.5.1", "0.5.1", false, "Versions are equal"),

                // Older versions (User is ahead)
                Arguments.of("0.5.2", "0.5.1", false, "Current is newer (patch)"),
                Arguments.of("0.6.0", "0.5.1", false, "Current is newer (minor)"),

                // Edge cases
                Arguments.of("UNKNOWN", "0.5.1", true, "Unknown version should prompt update"),
                Arguments.of("0.5.1-RELEASE", "0.5.1", false, "Handles non-numeric suffixes (equal)"),
                Arguments.of("0.5.1", "0.5.2-BETA", true, "Handles suffixes in latest (newer)")
        );
    }

    @Test
    @DisplayName("getLatestVersion returns latest non-prerelease version when latest is not a pre-release")
    void testGetLatestVersion_NonPrerelease() throws IOException
    {
        String latestReleaseJson = """
                {
                    "tag_name": "v0.6.2",
                    "prerelease": false,
                    "name": "Release 0.6.2"
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(latestReleaseJson)
                .setHeader("Content-Type", "application/json"));

        String baseUrl = "http://localhost:" + mockWebServer.getPort() + "/repos/test/repo";
        String result = OtherUtil.getLatestVersion(baseUrl);

        assertEquals("0.6.2", result);
    }

    @Test
    @DisplayName("getLatestVersion strips 'v' prefix from tag name")
    void testGetLatestVersion_StripsVPrefix() throws IOException
    {
        String latestReleaseJson = """
                {
                    "tag_name": "v1.0.0",
                    "prerelease": false
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(latestReleaseJson)
                .setHeader("Content-Type", "application/json"));

        String baseUrl = "http://localhost:" + mockWebServer.getPort() + "/repos/test/repo";
        String result = OtherUtil.getLatestVersion(baseUrl);

        assertEquals("1.0.0", result);
    }

    @Test
    @DisplayName("getLatestVersion returns version without 'v' prefix when tag doesn't have it")
    void testGetLatestVersion_NoVPrefix() throws IOException
    {
        String latestReleaseJson = """
                {
                    "tag_name": "0.6.2",
                    "prerelease": false
                }
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(latestReleaseJson)
                .setHeader("Content-Type", "application/json"));

        String baseUrl = "http://localhost:" + mockWebServer.getPort() + "/repos/test/repo";
        String result = OtherUtil.getLatestVersion(baseUrl);

        assertEquals("0.6.2", result);
    }

    @Test
    @DisplayName("getLatestVersion skips pre-release and returns latest non-prerelease from all releases")
    void testGetLatestVersion_SkipsPrerelease() throws IOException
    {
        // Latest release is a pre-release
        String latestReleaseJson = """
                {
                    "tag_name": "v0.7.0-beta",
                    "prerelease": true,
                    "name": "Pre-release 0.7.0-beta"
                }
                """;

        // All releases list with pre-release first, then non-prerelease
        String allReleasesJson = """
                [
                    {
                        "tag_name": "v0.7.0-beta",
                        "prerelease": true
                    },
                    {
                        "tag_name": "v0.6.2",
                        "prerelease": false
                    },
                    {
                        "tag_name": "v0.6.1",
                        "prerelease": false
                    }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(latestReleaseJson)
                .setHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(allReleasesJson)
                .setHeader("Content-Type", "application/json"));

        String baseUrl = "http://localhost:" + mockWebServer.getPort() + "/repos/test/repo";
        String result = OtherUtil.getLatestVersion(baseUrl);

        assertEquals("0.6.2", result);
    }

    @Test
    @DisplayName("getLatestVersion returns null when all releases are pre-releases")
    void testGetLatestVersion_AllPrereleases() throws IOException
    {
        // Latest release is a pre-release
        String latestReleaseJson = """
                {
                    "tag_name": "v0.7.0-beta",
                    "prerelease": true
                }
                """;

        // All releases are pre-releases
        String allReleasesJson = """
                [
                    {
                        "tag_name": "v0.7.0-beta",
                        "prerelease": true
                    },
                    {
                        "tag_name": "v0.7.0-alpha",
                        "prerelease": true
                    }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(latestReleaseJson)
                .setHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(allReleasesJson)
                .setHeader("Content-Type", "application/json"));

        String baseUrl = "http://localhost:" + mockWebServer.getPort() + "/repos/test/repo";
        String result = OtherUtil.getLatestVersion(baseUrl);

        assertNull(result);
    }

    @Test
    @DisplayName("getLatestVersion returns null when API returns empty response")
    void testGetLatestVersion_EmptyResponse() throws IOException
    {
        // First request returns empty object (no tag_name)
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("{}")
                .setHeader("Content-Type", "application/json"));

        // Second request (fallback to all releases) also returns empty
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody("[]")
                .setHeader("Content-Type", "application/json"));

        String baseUrl = "http://localhost:" + mockWebServer.getPort() + "/repos/test/repo";
        String result = OtherUtil.getLatestVersion(baseUrl);

        assertNull(result);
    }

    @Test
    @DisplayName("getLatestVersion returns null when API call fails")
    void testGetLatestVersion_ApiFailure() throws IOException
    {
        // First request fails
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        // Second request also fails
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(500)
                .setBody("Internal Server Error"));

        String baseUrl = "http://localhost:" + mockWebServer.getPort() + "/repos/test/repo";
        String result = OtherUtil.getLatestVersion(baseUrl);

        assertNull(result);
    }

    @Test
    @DisplayName("getLatestVersion handles multiple pre-releases before finding non-prerelease")
    void testGetLatestVersion_MultiplePrereleases() throws IOException
    {
        // Latest release is a pre-release
        String latestReleaseJson = """
                {
                    "tag_name": "v0.7.0-rc2",
                    "prerelease": true
                }
                """;

        // Multiple pre-releases before the first non-prerelease
        String allReleasesJson = """
                [
                    {
                        "tag_name": "v0.7.0-rc2",
                        "prerelease": true
                    },
                    {
                        "tag_name": "v0.7.0-rc1",
                        "prerelease": true
                    },
                    {
                        "tag_name": "v0.7.0-beta",
                        "prerelease": true
                    },
                    {
                        "tag_name": "v0.6.2",
                        "prerelease": false
                    }
                ]
                """;

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(latestReleaseJson)
                .setHeader("Content-Type", "application/json"));

        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setBody(allReleasesJson)
                .setHeader("Content-Type", "application/json"));

        String baseUrl = "http://localhost:" + mockWebServer.getPort() + "/repos/test/repo";
        String result = OtherUtil.getLatestVersion(baseUrl);

        assertEquals("0.6.2", result);
    }

    @Test
    @DisplayName("makeNonEmpty returns the string if not empty, otherwise returns zero-width space")
    public void testMakeNonEmpty()
    {
        assertEquals("test", OtherUtil.makeNonEmpty("test"));
        assertEquals("\u200B", OtherUtil.makeNonEmpty(null));
        assertEquals("\u200B", OtherUtil.makeNonEmpty(""));
    }
}
