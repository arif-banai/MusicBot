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
package com.jagrosh.jmusicbot.audio.bilibili;

import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;

/**
 * Bilibili answered a {@code playurl} request with a {@code v_voucher} challenge instead of
 * any stream. The caller decides what that means: a member-only video, or risk control.
 *
 * @author Arif Banai (arif-banai)
 */
public class BilibiliStreamWithheldException extends FriendlyException
{
    public BilibiliStreamWithheldException()
    {
        super("Bilibili withheld the stream for this video", Severity.COMMON, null);
    }
}
