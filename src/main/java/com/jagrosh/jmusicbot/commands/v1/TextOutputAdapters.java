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
package com.jagrosh.jmusicbot.commands.v1;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.commands.BaseOutputAdapter;
import net.dv8tion.jda.api.entities.Message;

import java.util.function.Consumer;

/**
 * Reusable OutputAdapter implementations for text commands.
 */
public final class TextOutputAdapters
{
    private TextOutputAdapters() {} // Utility class

    /**
     * Simple OutputAdapter for direct CommandEvent replies.
     * Used for commands that need basic reply functionality.
     */
    public static class SimpleOutputAdapter extends BaseOutputAdapter
    {
        private final CommandEvent event;

        public SimpleOutputAdapter(CommandEvent event)
        {
            this.event = event;
        }

        @Override
        public void replySuccess(String content)
        {
            event.replySuccess(content);
        }

        @Override
        public void replyError(String content)
        {
            event.replyError(content);
        }

        @Override
        public void replyWarning(String content)
        {
            event.replyWarning(content);
        }
    }

    /**
     * OutputAdapter for direct CommandEvent replies (before any loading message is sent).
     * Used when no args are provided and we need to show help or handle empty input.
     */
    public static class CommandEventOutputAdapter extends BaseOutputAdapter
    {
        private final CommandEvent event;
        private final String commandName;
        private final Command[] children;

        public CommandEventOutputAdapter(CommandEvent event, String commandName, Command[] children)
        {
            this.event = event;
            this.commandName = commandName;
            this.children = children;
        }

        @Override
        public void replySuccess(String content)
        {
            event.replySuccess(content);
        }

        @Override
        public void replyError(String content)
        {
            event.replyError(content);
        }

        @Override
        public void replyWarning(String content)
        {
            event.replyWarning(content);
        }

        @Override
        public void onShowHelp()
        {
            StringBuilder builder = new StringBuilder(event.getClient().getWarning() + " Play Commands:\n");
            builder.append("\n`").append(event.getClient().getPrefix()).append(commandName).append(" <song title>` - plays the first result from Youtube");
            builder.append("\n`").append(event.getClient().getPrefix()).append(commandName).append(" <URL>` - plays the provided song, playlist, or stream");
            for (Command cmd : children)
            {
                builder.append("\n`").append(event.getClient().getPrefix()).append(commandName).append(" ")
                        .append(cmd.getName()).append(" ").append(cmd.getArguments()).append("` - ").append(cmd.getHelp());
            }
            event.reply(builder.toString());
        }
    }

    /**
     * OutputAdapter for editing an existing message after a loading message was sent.
     * Used when args are provided and we show a loading message first.
     */
    public static class MessageEditOutputAdapter extends BaseOutputAdapter
    {
        private final Message message;

        public MessageEditOutputAdapter(Message message)
        {
            this.message = message;
        }

        @Override
        public void editMessage(String content)
        {
            message.editMessage(content).queue();
        }

        @Override
        public void editMessage(String content, Consumer<Message> onSuccess)
        {
            message.editMessage(content).queue(onSuccess);
        }
    }
}
