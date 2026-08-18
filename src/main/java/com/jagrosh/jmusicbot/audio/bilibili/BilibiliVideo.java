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

/**
 * Metadata for one playable Bilibili video page.
 *
 * <p>A {@code cid} of 0 means the page id is not yet known. Search results arrive without
 * one, so tracks built from them resolve it at playback time using the {@code bvid}.
 *
 * @param bvid the video id, e.g. {@code BV1DTbv6xEHK}
 * @param cid the page id, or 0 if not yet resolved
 * @param title display title, already including the part name for multi-page videos
 * @param author the uploader's name
 * @param durationMs the length in milliseconds
 * @param thumbnailUrl an absolute https URL
 *
 * @author Arif Banai (arif-banai)
 */
public record BilibiliVideo(String bvid, long cid, String title, String author,
                            long durationMs, String thumbnailUrl)
{
}
