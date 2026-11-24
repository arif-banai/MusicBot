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

import com.jagrosh.jmusicbot.DiscordService;
import com.jagrosh.jmusicbot.JMusicBot;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DiscordService cache flag Tests")
class DiscordServiceCacheTest
{
    @Test
    @DisplayName("every cache flag left on has its gateway intent, so JDA logs no missing-intent warning")
    void noCacheFlagNeedsAnIntentWeDoNotRequest()
    {
        Set<GatewayIntent> intents = EnumSet.copyOf(Arrays.asList(JMusicBot.INTENTS));
        List<CacheFlag> missing = EnumSet.allOf(CacheFlag.class).stream()
                .filter(flag -> !DiscordService.DISABLED_CACHE.contains(flag))
                .filter(flag -> flag.getRequiredIntent() != null && !intents.contains(flag.getRequiredIntent()))
                .toList();

        assertEquals(List.of(), missing, "disable these in DiscordService.DISABLED_CACHE or request their intents");
    }

    @Test
    @DisplayName("the soundboard cache is off (issue #98)")
    void soundboardCacheIsOff()
    {
        assertTrue(DiscordService.DISABLED_CACHE.contains(CacheFlag.SOUNDBOARD_SOUNDS));
    }
}
