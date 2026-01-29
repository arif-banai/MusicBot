package com.jagrosh.jmusicbot.commands;

import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.service.MusicService;
import net.dv8tion.jda.api.entities.Message;

import java.util.function.Consumer;

/**
 * Base implementation of OutputAdapter with no-op defaults.
 * Subclasses only need to override the methods they actually use.
 */
public abstract class BaseOutputAdapter implements MusicService.OutputAdapter
{
    @Override
    public void replySuccess(String content)
    {
        // No-op by default
    }

    @Override
    public void replyError(String content)
    {
        // No-op by default
    }

    @Override
    public void replyWarning(String content)
    {
        // No-op by default
    }

    @Override
    public void editMessage(String content)
    {
        // No-op by default
    }

    @Override
    public void editMessage(String content, Consumer<Message> onSuccess)
    {
        // No-op by default
    }

    @Override
    public void editNowPlaying(AudioHandler handler)
    {
        // No-op by default
    }

    @Override
    public void editNoMusic(AudioHandler handler)
    {
        // No-op by default
    }

    @Override
    public void onShowHelp()
    {
        // No-op by default
    }
}
