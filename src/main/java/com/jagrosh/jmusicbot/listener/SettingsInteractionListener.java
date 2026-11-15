package com.jagrosh.jmusicbot.listener;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.v2.admin.SettingsPanelRenderer;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.listener.interaction.InteractionGuards;
import com.jagrosh.jmusicbot.settings.NowPlayingButtonsMode;
import com.jagrosh.jmusicbot.settings.NowPlayingLayoutMode;
import com.jagrosh.jmusicbot.settings.QueueType;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.EntitySelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.modals.Modal;
import net.dv8tion.jda.api.requests.ErrorResponse;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageEditBuilder;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Optional;

public class SettingsInteractionListener extends ListenerAdapter
{
    private final Bot bot;

    public SettingsInteractionListener(Bot bot)
    {
        this.bot = bot;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event)
    {
        if (!event.getComponentId().startsWith("settings_"))
            return;
        handleSettingsButton(event);
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event)
    {
        if (!event.getModalId().startsWith("settings_modal_"))
            return;
        handleSettingsModal(event);
    }

    @Override
    public void onEntitySelectInteraction(@NotNull EntitySelectInteractionEvent event)
    {
        if (!event.getComponentId().startsWith("settings_entity_"))
            return;
        handleSettingsEntitySelect(event);
    }

    private void handleSettingsButton(ButtonInteractionEvent event)
    {
        if (!InteractionGuards.requireGuildAndMember(event))
            return;

        Optional<ComponentIdParsers.SettingsButtonId> parsed = ComponentIdParsers.parseSettingsButtonId(event.getComponentId());
        if (parsed.isEmpty())
        {
            event.reply(expiredMessage()).setEphemeral(true).queue();
            return;
        }
        ComponentIdParsers.SettingsButtonId id = parsed.get();
        if (event.getUser().getIdLong() != id.userId())
        {
            event.reply("Only the user who opened this settings panel can use it.").setEphemeral(true).queue();
            return;
        }
        if (!hasSettingsPermission(event.getMember(), event.getUser().getIdLong()))
        {
            event.reply("You need the Manage Server permission to change settings.").setEphemeral(true).queue();
            return;
        }

        Settings settings = bot.getSettingsManager().getSettings(event.getGuild());

        switch (id.action())
        {
            case SettingsPanelRenderer.ACTION_ENUM -> {
                if (!applyEnumUpdate(id.key(), id.value(), settings, event.getGuild()))
                {
                    event.reply(expiredMessage()).setEphemeral(true).queue();
                    return;
                }
                refreshPanel(event, settings);
            }
            case SettingsPanelRenderer.ACTION_TOGGLE -> {
                if (!applyToggleCycle(id.key(), settings, event.getGuild()))
                {
                    event.reply(expiredMessage()).setEphemeral(true).queue();
                    return;
                }
                refreshPanel(event, settings);
            }
            case SettingsPanelRenderer.ACTION_OPEN -> {
                if ("settc".equals(id.key()) || "setvc".equals(id.key()) || "setdj".equals(id.key()))
                {
                    long originalPanelMessageId = event.getMessageIdLong();
                    var menu = SettingsPanelRenderer.buildEntitySelectMenu(id.key(), id.userId(), originalPanelMessageId);
                    var clearButton = SettingsPanelRenderer.buildEntityClearButton(id.key(), id.userId(), originalPanelMessageId);
                    if (menu == null || clearButton == null)
                    {
                        event.reply(expiredMessage()).setEphemeral(true).queue();
                        return;
                    }
                    String prompt = switch (id.key())
                    {
                        case "settc" -> "Select a text channel:";
                        case "setvc" -> "Select a voice channel:";
                        case "setdj" -> "Select the DJ role:";
                        default -> null;
                    };
                    if (prompt == null)
                    {
                        event.reply(expiredMessage()).setEphemeral(true).queue();
                        return;
                    }
                    event.reply(prompt)
                            .setEphemeral(false)
                            .addComponents(net.dv8tion.jda.api.components.actionrow.ActionRow.of(menu),
                                    net.dv8tion.jda.api.components.actionrow.ActionRow.of(clearButton))
                            .queue();
                    return;
                }
                Modal modal = SettingsPanelRenderer.buildModal(id.key(), id.userId());
                if (modal == null)
                {
                    event.reply(expiredMessage()).setEphemeral(true).queue();
                    return;
                }
                event.replyModal(modal).queue();
            }
            case SettingsPanelRenderer.ACTION_CLEAR -> {
                if (!applyClear(id.key(), settings))
                {
                    event.reply(expiredMessage()).setEphemeral(true).queue();
                    return;
                }

                if (("settc".equals(id.key()) || "setvc".equals(id.key()) || "setdj".equals(id.key()))
                        && id.value() != null && !id.value().isBlank())
                {
                    long originalPanelMessageId;
                    try
                    {
                        originalPanelMessageId = Long.parseLong(id.value());
                    }
                    catch (NumberFormatException ex)
                    {
                        event.reply(expiredMessage()).setEphemeral(true).queue();
                        return;
                    }

                    String invokerName = event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getName();
                    event.deferEdit().queue(hook -> {
                        event.getMessage().delete().queue(
                                ignored -> {
                                },
                                err -> {
                                });
                        refreshPanelByMessageId(event.getGuild(), event.getChannel(),
                                originalPanelMessageId, id.userId(), invokerName, hook);
                    });
                    return;
                }

                refreshPanel(event, settings);
            }
            case SettingsPanelRenderer.ACTION_REFRESH -> refreshPanel(event, settings);
            case SettingsPanelRenderer.ACTION_CLOSE -> event.deferEdit().queue(hook ->
                    event.getMessage().delete().queue(
                            ignored -> {
                            },
                            err -> hook.sendMessage("Failed to close settings panel.").setEphemeral(true).queue()
                    ));
            default -> event.reply(expiredMessage()).setEphemeral(true).queue();
        }
    }

    private void handleSettingsModal(ModalInteractionEvent event)
    {
        if (!InteractionGuards.requireGuildAndMember(event))
            return;

        Optional<ComponentIdParsers.SettingsModalId> parsed = ComponentIdParsers.parseSettingsModalId(event.getModalId());
        if (parsed.isEmpty())
        {
            event.reply(expiredMessage()).setEphemeral(true).queue();
            return;
        }

        ComponentIdParsers.SettingsModalId id = parsed.get();
        if (event.getUser().getIdLong() != id.userId())
        {
            event.reply("Only the user who opened this settings dialog can submit it.").setEphemeral(true).queue();
            return;
        }
        if (!hasSettingsPermission(event.getMember(), event.getUser().getIdLong()))
        {
            event.reply("You need the Manage Server permission to change settings.").setEphemeral(true).queue();
            return;
        }

        Settings settings = bot.getSettingsManager().getSettings(event.getGuild());
        String error = applyModalUpdate(event, id.key(), settings);
        if (error != null)
        {
            event.reply(error).setEphemeral(true).queue();
            return;
        }

        String invokerName = event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getName();
        event.reply(new MessageCreateBuilder()
                        .setContent("Settings updated.")
                        .setComponents(SettingsPanelRenderer.buildSettingsMessageComponents(
                                event.getGuild(), settings, bot.getConfig(), id.userId(), invokerName))
                        .useComponentsV2()
                        .build())
                .setEphemeral(true)
                .queue();
    }

    private void handleSettingsEntitySelect(EntitySelectInteractionEvent event)
    {
        if (!InteractionGuards.requireGuildAndMember(event))
            return;

        Optional<ComponentIdParsers.SettingsEntitySelectId> parsed =
                ComponentIdParsers.parseSettingsEntitySelectId(event.getComponentId());
        if (parsed.isEmpty())
        {
            event.reply(expiredMessage()).setEphemeral(true).queue();
            return;
        }

        ComponentIdParsers.SettingsEntitySelectId id = parsed.get();
        if (event.getUser().getIdLong() != id.userId())
        {
            event.reply("Only the user who opened this settings panel can use it.").setEphemeral(true).queue();
            return;
        }
        if (!hasSettingsPermission(event.getMember(), event.getUser().getIdLong()))
        {
            event.reply("You need the Manage Server permission to change settings.").setEphemeral(true).queue();
            return;
        }

        Settings settings = bot.getSettingsManager().getSettings(event.getGuild());
        String error = applyEntitySelectUpdate(event, id.key(), settings);
        if (error != null)
        {
            event.reply(error).setEphemeral(true).queue();
            return;
        }
        Long originalPanelMessageId = id.originalPanelMessageId();
        if (originalPanelMessageId == null || originalPanelMessageId <= 0)
        {
            String invokerName = event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getName();
            event.editMessage(new MessageEditBuilder()
                            .setContent("")
                            .setEmbeds(List.of())
                            .setComponents(SettingsPanelRenderer.buildSettingsMessageComponents(
                                    event.getGuild(), settings, bot.getConfig(), id.userId(), invokerName))
                            .useComponentsV2()
                            .build())
                    .queue();
            return;
        }

        String invokerName = event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getName();
        event.deferEdit().queue(hook -> {
            event.getMessage().delete().queue(
                    ignored -> {
                    },
                    err -> {
                    });
            refreshPanelByMessageId(event.getGuild(), event.getChannel(),
                    originalPanelMessageId, id.userId(), invokerName, hook);
        });
    }

    private boolean applyEnumUpdate(String key, String value, Settings settings, Guild guild)
    {
        switch (key)
        {
            case "queue" -> {
                QueueType type;
                if ("linear".equals(value))
                    type = QueueType.LINEAR;
                else if ("fair".equals(value))
                    type = QueueType.FAIR;
                else
                    return false;

                if (settings.getQueueType() != type)
                {
                    settings.setQueueType(type);
                    AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                    if (handler != null)
                        handler.setQueueType(type);
                }
                return true;
            }
            case "layout" -> {
                NowPlayingLayoutMode mode = NowPlayingLayoutMode.fromInput(value);
                if (mode == null)
                    return false;
                settings.setNowPlayingLayoutMode(mode);
                return true;
            }
            case "npbuttons" -> {
                NowPlayingButtonsMode mode = NowPlayingButtonsMode.fromInput(value);
                if (mode == null)
                    return false;
                settings.setNowPlayingButtonsMode(mode);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean applyToggleCycle(String key, Settings settings, Guild guild)
    {
        switch (key)
        {
            case "queue" -> {
                QueueType next = settings.getQueueType() == QueueType.LINEAR ? QueueType.FAIR : QueueType.LINEAR;
                settings.setQueueType(next);
                AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                if (handler != null)
                    handler.setQueueType(next);
                return true;
            }
            case "layout" -> {
                NowPlayingLayoutMode current = settings.getNowPlayingLayoutMode();
                NowPlayingLayoutMode next = switch (current)
                {
                    case FULL -> NowPlayingLayoutMode.MINIMAL;
                    case MINIMAL -> NowPlayingLayoutMode.INHERIT;
                    case INHERIT -> NowPlayingLayoutMode.FULL;
                };
                settings.setNowPlayingLayoutMode(next);
                return true;
            }
            case "npbuttons" -> {
                NowPlayingButtonsMode current = settings.getNowPlayingButtonsMode();
                NowPlayingButtonsMode next = switch (current)
                {
                    case ON -> NowPlayingButtonsMode.OFF;
                    case OFF -> NowPlayingButtonsMode.INHERIT;
                    case INHERIT -> NowPlayingButtonsMode.ON;
                };
                settings.setNowPlayingButtonsMode(next);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    private boolean applyClear(String key, Settings settings)
    {
        switch (key)
        {
            case "settc" -> settings.setTextChannel(null);
            case "setvc" -> settings.setVoiceChannel(null);
            case "setdj" -> settings.setDJRole(null);
            case "prefix" -> settings.setPrefix(null);
            default -> {
                return false;
            }
        }
        return true;
    }

    private String applyModalUpdate(ModalInteractionEvent event, String key, Settings settings)
    {
        Guild guild = event.getGuild();
        assert guild != null;

        switch (key)
        {
            case "prefix" -> {
                String value = SettingsPanelRenderer.modalValue(event, "prefix_value");
                settings.setPrefix(value.isEmpty() ? null : value);
                return null;
            }
            case "setskip" -> {
                String value = SettingsPanelRenderer.modalValue(event, "setskip_value");
                if (value.isEmpty())
                    return "Please enter an integer between 0 and 100.";
                try
                {
                    int percent = Integer.parseInt(value);
                    if (percent < 0 || percent > 100)
                        return "Skip percentage must be between 0 and 100.";
                    settings.setSkipRatio(percent / 100.0);
                    return null;
                }
                catch (NumberFormatException ex)
                {
                    return "Please enter an integer between 0 and 100.";
                }
            }
            default -> {
                return expiredMessage();
            }
        }
    }

    private String applyEntitySelectUpdate(EntitySelectInteractionEvent event, String key, Settings settings)
    {
        switch (key)
        {
            case "settc" -> {
                var channels = event.getMentions().getChannels(TextChannel.class);
                if (channels.isEmpty())
                    return "Please choose a text channel.";
                TextChannel textChannel = channels.get(0);
                settings.setTextChannel(textChannel);
                return null;
            }
            case "setvc" -> {
                var channels = event.getMentions().getChannels(VoiceChannel.class);
                if (channels.isEmpty())
                    return "Please choose a voice channel.";
                VoiceChannel voiceChannel = channels.get(0);
                settings.setVoiceChannel(voiceChannel);
                return null;
            }
            case "setdj" -> {
                var roles = event.getMentions().getRoles();
                if (roles.isEmpty())
                    return "Please choose a role.";
                Role role = roles.get(0);
                settings.setDJRole(role);
                return null;
            }
            default -> {
                return expiredMessage();
            }
        }
    }

    private void refreshPanel(ButtonInteractionEvent event, Settings settings)
    {
        String invokerName = event.getMember() != null ? event.getMember().getEffectiveName() : event.getUser().getName();
        event.editMessage(new MessageEditBuilder()
                        .setContent("")
                        .setEmbeds(List.of())
                        .setComponents(SettingsPanelRenderer.buildSettingsMessageComponents(
                                event.getGuild(), settings, bot.getConfig(), event.getUser().getIdLong(), invokerName))
                        .useComponentsV2()
                        .build())
                .queue();
    }

    private void refreshPanelByMessageId(Guild guild, MessageChannel channel, long panelMessageId, long userId,
                                         String invokerName, net.dv8tion.jda.api.interactions.InteractionHook hook)
    {
        if (guild == null || channel == null)
        {
            hook.sendMessage(expiredMessage()).setEphemeral(true).queue();
            return;
        }
        // JDA 6.7+ hands interactions a detached channel snapshot when the bot cannot access the
        // channel; retrieveMessageById would throw instead of failing the RestAction.
        if (channel.isDetached())
        {
            hook.sendMessage("I can't access this channel anymore, so the settings panel can't be refreshed. Run `/settings` again.")
                    .setEphemeral(true).queue();
            return;
        }

        Settings settings = bot.getSettingsManager().getSettings(guild);
        channel.retrieveMessageById(panelMessageId)
                .queue(message -> message.editMessage(new MessageEditBuilder()
                                        .setContent("")
                                        .setEmbeds(List.of())
                                        .setComponents(SettingsPanelRenderer.buildSettingsMessageComponents(
                                                guild, settings, bot.getConfig(), userId, invokerName))
                                        .useComponentsV2()
                                        .build())
                                .queue(),
                        err -> {
                            if (err instanceof ErrorResponseException ex
                                    && ex.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE)
                            {
                                hook.sendMessage(expiredMessage()).setEphemeral(true).queue();
                            }
                            else
                            {
                                hook.sendMessage("Failed to refresh settings panel. Run `/settings` again.")
                                        .setEphemeral(true).queue();
                            }
                        });
    }

    private String expiredMessage()
    {
        return "This settings panel is expired. Run `/settings` again.";
    }

    /**
     * Same permission as admin settings commands: bot owner or MANAGE_SERVER.
     */
    private boolean hasSettingsPermission(Member member, long userId)
    {
        if (member == null)
            return false;
        boolean isOwner = userId == bot.getConfig().getOwnerId();
        boolean hasPermission = member.hasPermission(Permission.MANAGE_SERVER);
        return isOwner || hasPermission;
    }
}
