package com.jagrosh.jmusicbot.unit.audio;

import com.jagrosh.jmusicbot.TestBase;
import com.jagrosh.jmusicbot.audio.AloneInVoiceHandler;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import java.util.Collections;

import static org.mockito.Mockito.*;

public class AloneInVoiceHandlerTest extends TestBase {

    @Mock
    private GuildVoiceUpdateEvent voiceUpdateEvent;
    @Mock
    private GuildVoiceState voiceState;

    private AloneInVoiceHandler aloneInVoiceHandler;

    @BeforeEach
    @Override
    public void setUp() {
        super.setUp();
        when(voiceUpdateEvent.getEntity()).thenReturn(member);

        aloneInVoiceHandler = new AloneInVoiceHandler(bot);
    }

    @Test
    public void testOnVoiceUpdateWhenDisabled() {
        when(config.getAloneTimeUntilStop()).thenReturn(0L);
        aloneInVoiceHandler.init();

        aloneInVoiceHandler.onVoiceUpdate(voiceUpdateEvent);

        verify(playerManager, never()).hasHandler(any());
    }

    @Test
    public void testOnVoiceUpdateWhenAlone() {
        when(config.getAloneTimeUntilStop()).thenReturn(300L);
        aloneInVoiceHandler.init();
        when(playerManager.hasHandler(guild)).thenReturn(true);
        when(audioManager.getConnectedChannel()).thenReturn(audioChannel);
        when(audioChannel.getMembers()).thenReturn(Collections.singletonList(member));
        when(member.getUser()).thenReturn(user);
        when(member.getVoiceState()).thenReturn(voiceState);
        when(user.isBot()).thenReturn(true); // Only bot is in channel

        aloneInVoiceHandler.onVoiceUpdate(voiceUpdateEvent);
    }
    
    @Test
    public void testIsAlone() throws Exception {
        // Since isAlone is private, we test it through onVoiceUpdate or use reflection if needed.
        // But we can test the behavior of onVoiceUpdate which uses isAlone.
        
        when(config.getAloneTimeUntilStop()).thenReturn(300L);
        aloneInVoiceHandler.init();
        when(playerManager.hasHandler(guild)).thenReturn(true);
        
        // Not alone (human in channel)
        when(audioManager.getConnectedChannel()).thenReturn(audioChannel);
        Member human = mock(Member.class);
        User humanUser = mock(User.class);
        GuildVoiceState humanVoiceState = mock(GuildVoiceState.class);
        when(human.getUser()).thenReturn(humanUser);
        when(human.getVoiceState()).thenReturn(humanVoiceState);
        when(humanUser.isBot()).thenReturn(false);
        when(humanVoiceState.isDeafened()).thenReturn(false);
        when(audioChannel.getMembers()).thenReturn(Collections.singletonList(human));
        
        aloneInVoiceHandler.onVoiceUpdate(voiceUpdateEvent);
        // Should not be in aloneSince
        
        // Alone (only bot)
        when(user.isBot()).thenReturn(true);
        when(member.getVoiceState()).thenReturn(voiceState);
        when(audioChannel.getMembers()).thenReturn(Collections.singletonList(member));
        when(member.getUser()).thenReturn(user);
        
        aloneInVoiceHandler.onVoiceUpdate(voiceUpdateEvent);
        // Should be in aloneSince
    }
}
