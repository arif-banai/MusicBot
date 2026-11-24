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

import com.jagrosh.jmusicbot.commands.v1.music.BiliSearchCmd;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BiliSearchCmd result layout Tests")
class BiliSearchCmdFormatTest
{
    private static final String URI = "https://www.bilibili.com/video/BV1r3y5zTE8A";

    @Test
    @DisplayName("shows duration and linked title, with the uploader on a second line")
    void showsUploaderBelow()
    {
        assertEquals("`[03:18]` [**Rainy night lofi**](" + URI + ")\n  by 高粱地里吃过虧",
                BiliSearchCmd.formatChoice("Rainy night lofi", "高粱地里吃过虧", 198_000L, URI));
    }

    @Test
    @DisplayName("trims long titles without splitting an emoji")
    void trimsLongTitles()
    {
        String title = "Lofi Chill Music🌧️丨午夜过后，道路看起来更美丨沉闷的Lofi音乐，助你舒缓过度思虑的思绪以及更多更多更多的文字";
        String line = BiliSearchCmd.formatChoice(title, "", 1000L, URI);

        String shown = line.substring(line.indexOf("[**") + 3, line.indexOf("**]"));
        assertTrue(shown.endsWith("…"), shown);
        assertEquals(55, shown.codePointCount(0, shown.length()), shown);
        assertFalse(Character.isHighSurrogate(shown.charAt(shown.length() - 2)), shown);
    }

    @Test
    @DisplayName("keeps short titles whole")
    void keepsShortTitles()
    {
        assertTrue(BiliSearchCmd.formatChoice("短标题", "up", 1000L, URI).contains("[**短标题**]"));
    }

    @Test
    @DisplayName("escapes brackets and markdown so the link survives titles like [Lofi|雨天]")
    void escapesMarkdown()
    {
        String line = BiliSearchCmd.formatChoice("[Lofi|雨天|像素]*rain*", "a_b", 1000L, URI);
        assertTrue(line.contains("[**\\[Lofi\\|雨天\\|像素\\]\\*rain\\***](" + URI + ")"), line);
        assertTrue(line.endsWith("by a\\_b"), line);
    }

    @Test
    @DisplayName("leaves out the uploader line when there is no uploader")
    void omitsMissingUploader()
    {
        assertFalse(BiliSearchCmd.formatChoice("t", null, 1000L, URI).contains("\n"));
    }
}
