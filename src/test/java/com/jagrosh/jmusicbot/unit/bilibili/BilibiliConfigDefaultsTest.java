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
package com.jagrosh.jmusicbot.unit.bilibili;

import com.jagrosh.jmusicbot.config.model.ConfigOption;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Bilibili config defaults Tests")
class BilibiliConfigDefaultsTest
{
    private final Config reference = ConfigFactory.parseResources("reference.conf");

    @Test
    @DisplayName("the source is on by default")
    void sourceOnByDefault()
    {
        assertTrue(reference.getBoolean("playback.audioSources.bilibili"));
    }

    @Test
    @DisplayName("the browser cookie is opt-in: no cookies are sent unless the owner turns it on")
    void browserCookieOffByDefault()
    {
        assertFalse(ConfigOption.BILIBILI_BROWSER_COOKIE.getBoolean(reference));
    }
}
