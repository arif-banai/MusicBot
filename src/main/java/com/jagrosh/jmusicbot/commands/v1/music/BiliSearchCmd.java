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
package com.jagrosh.jmusicbot.commands.v1.music;

import com.jagrosh.jmusicbot.Bot;

/**
 * Searches Bilibili (哔哩哔哩) for a query and offers the results for selection.
 *
 * @author Arif Banai (arif-banai)
 */
public class BiliSearchCmd extends SearchCmd
{
    public BiliSearchCmd(Bot bot)
    {
        super(bot);
        this.searchPrefix = "bilisearch:";
        this.name = "bilisearch";
        this.help = "searches Bilibili for a provided query";
        this.aliases = bot.getConfig().getAliases(this.name);
    }
}
