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
package com.jagrosh.jmusicbot.commands.v2;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.BaseOutputAdapter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.function.Consumer;

/**
 * Reusable OutputAdapter implementations for slash commands.
 */
public final class SlashOutputAdapters
{
    private SlashOutputAdapters() {} // Utility class

    /**
     * OutputAdapter for direct SlashCommandEvent replies (before any response is sent).
     * Errors and warnings are sent as ephemeral messages.
     */
    public static class SlashEventOutputAdapter extends BaseOutputAdapter
    {
        private final SlashCommandEvent event;

        public SlashEventOutputAdapter(SlashCommandEvent event)
        {
            this.event = event;
        }

        @Override
        public void replySuccess(String content)
        {
            event.reply(content).queue();
        }

        @Override
        public void replyError(String content)
        {
            event.reply(content).setEphemeral(true).queue();
        }

        @Override
        public void replyWarning(String content)
        {
            event.reply(content).setEphemeral(true).queue();
        }

        @Override
        public void editMessage(String content)
        {
            event.reply(content).queue();
        }

        @Override
        public void editMessage(String content, Consumer<Message> onSuccess)
        {
            event.reply(content).queue(hook -> hook.retrieveOriginal().queue(onSuccess));
        }

        @Override
        public void editNowPlaying(AudioHandler handler)
        {
            event.reply(handler.getNowPlaying(event.getJDA())).queue();
        }

        @Override
        public void editNoMusic(AudioHandler handler)
        {
            event.reply(handler.getNoMusicPlaying(event.getJDA())).queue();
        }

        @Override
        public void onShowHelp()
        {
            event.reply(event.getClient().getWarning() + " Please include a song title or URL!").setEphemeral(true).queue();
        }
    }

    /**
     * OutputAdapter for editing an existing interaction response via InteractionHook.
     * Used after a loading message has already been sent.
     */
    public static class InteractionHookOutputAdapter extends BaseOutputAdapter
    {
        private final InteractionHook hook;
        private final JDA jda;
        private final String warningEmoji;

        public InteractionHookOutputAdapter(InteractionHook hook, JDA jda, String warningEmoji)
        {
            this.hook = hook;
            this.jda = jda;
            this.warningEmoji = warningEmoji;
        }

        @Override
        public void replySuccess(String content)
        {
            hook.editOriginal(content).queue();
        }

        @Override
        public void replyError(String content)
        {
            hook.editOriginal(content).queue();
        }

        @Override
        public void replyWarning(String content)
        {
            hook.editOriginal(content).queue();
        }

        @Override
        public void editMessage(String content)
        {
            hook.editOriginal(content).queue();
        }

        @Override
        public void editMessage(String content, Consumer<Message> onSuccess)
        {
            hook.editOriginal(content).queue(onSuccess);
        }

        @Override
        public void editNowPlaying(AudioHandler handler)
        {
            hook.editOriginal(MessageEditData.fromCreateData(handler.getNowPlaying(jda))).queue();
        }

        @Override
        public void editNoMusic(AudioHandler handler)
        {
            hook.editOriginal(MessageEditData.fromCreateData(handler.getNoMusicPlaying(jda))).queue();
        }

        @Override
        public void onShowHelp()
        {
            hook.editOriginal(warningEmoji + " Please include a song title or URL!").queue();
        }
    }
}
