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
package com.jagrosh.jmusicbot.testutil.listener;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageCreateAction;
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction;
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction;
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Models JDA's acknowledgement rules on a mocked {@link ButtonInteractionEvent}.
 * <p>
 * Every interaction callback ({@code reply}, {@code deferEdit}, {@code editMessage*}) counts as an
 * acknowledgement when queued, as in JDA's {@code InteractionCallbackImpl.tryAck}, which fails every
 * one after the first. {@code isAcknowledged()} reflects that state, and
 * {@code getHook()} returns a mock hook whose follow-ups and edits are recorded.
 */
@SuppressWarnings("unchecked")
public final class InteractionAckTracker
{
    private final ButtonInteractionEvent event;
    private final InteractionHook hook = mock(InteractionHook.class);
    private final WebhookMessageCreateAction<Message> followup = mock(WebhookMessageCreateAction.class);
    private final WebhookMessageEditAction<Message> hookEdit = mock(WebhookMessageEditAction.class);
    private final List<String> acks = Collections.synchronizedList(new ArrayList<>());

    private InteractionAckTracker(ButtonInteractionEvent event, ReplyCallbackAction replyAction)
    {
        this.event = event;

        when(event.isAcknowledged()).thenAnswer(inv -> !acks.isEmpty());
        when(event.getHook()).thenReturn(hook);

        doAnswer(inv -> ack("reply")).when(replyAction).queue();
        when(event.reply(anyString())).thenReturn(replyAction);
        when(replyAction.setEphemeral(anyBoolean())).thenReturn(replyAction);

        MessageEditCallbackAction deferAction = editCallback("deferEdit");
        when(event.deferEdit()).thenReturn(deferAction);
        MessageEditCallbackAction editEmbedsAction = editCallback("editMessageEmbeds");
        when(event.editMessageEmbeds(any(MessageEmbed[].class))).thenReturn(editEmbedsAction);
        when(event.editMessageEmbeds(anyCollection())).thenReturn(editEmbedsAction);
        MessageEditCallbackAction editMessageAction = editCallback("editMessage");
        when(event.editMessage(anyString())).thenReturn(editMessageAction);

        when(hook.sendMessage(anyString())).thenReturn(followup);
        when(followup.setEphemeral(anyBoolean())).thenReturn(followup);
        when(hook.editOriginal(anyString())).thenReturn(hookEdit);
        when(hook.editOriginalEmbeds(any(MessageEmbed[].class))).thenReturn(hookEdit);
        when(hook.editOriginalEmbeds(anyCollection())).thenReturn(hookEdit);
        when(hookEdit.setComponents(anyCollection())).thenReturn(hookEdit);
        when(hookEdit.setEmbeds(anyCollection())).thenReturn(hookEdit);
    }

    public static InteractionAckTracker attach(ButtonInteractionEvent event, ReplyCallbackAction replyAction)
    {
        return new InteractionAckTracker(event, replyAction);
    }

    private MessageEditCallbackAction editCallback(String name)
    {
        MessageEditCallbackAction action = mock(MessageEditCallbackAction.class);
        when(action.setComponents(anyCollection())).thenReturn(action);
        when(action.setEmbeds(anyCollection())).thenReturn(action);
        when(action.setEmbeds()).thenReturn(action);
        when(action.setComponents()).thenReturn(action);
        doAnswer(inv -> ack(name)).when(action).queue();
        return action;
    }

    private Object ack(String name)
    {
        // JDA would fail every acknowledgement after the first with IllegalStateException
        // ("already been acknowledged"); record them all so tests can assert there was exactly one.
        acks.add(name);
        return null;
    }

    /** Every acknowledgement queued so far, in order. A correct interaction has exactly one. */
    public List<String> acks()
    {
        return List.copyOf(acks);
    }

    public InteractionHook hook()
    {
        return hook;
    }

    /** The follow-up action returned by {@code hook.sendMessage}. */
    public WebhookMessageCreateAction<Message> followup()
    {
        return followup;
    }

    /** The edit action returned by {@code hook.editOriginal*}. */
    public WebhookMessageEditAction<Message> hookEdit()
    {
        return hookEdit;
    }

    public ButtonInteractionEvent event()
    {
        return event;
    }
}
