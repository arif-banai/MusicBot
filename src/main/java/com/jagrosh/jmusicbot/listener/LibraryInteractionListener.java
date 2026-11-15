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
package com.jagrosh.jmusicbot.listener;

import java.awt.Color;
import java.util.List;
import java.util.Optional;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.v1.DJCommand;
import com.jagrosh.jmusicbot.commands.v2.dj.LibraryPanelSlashCmd;
import com.jagrosh.jmusicbot.commands.v2.dj.LibraryUnlinkSlashCmd;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.listener.interaction.InteractionGuards;
import com.jagrosh.jmusicbot.listener.interaction.LibraryPanelTokens;
import com.jagrosh.jmusicbot.listener.interaction.OutputAdapters;
import com.jagrosh.jmusicbot.service.MusicService;
import com.jagrosh.jmusicbot.ytdlp.LibraryAudioTrack;
import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.LibraryKeys;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.label.Label;
import net.dv8tion.jda.api.components.textinput.TextInput;
import net.dv8tion.jda.api.components.textinput.TextInputStyle;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.callbacks.IMessageEditCallback;
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback;
import net.dv8tion.jda.api.modals.Modal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Handles {@code /library panel} interactions ({@code lib_*}): the stats/Info/Search/Rescan
 * panel, the current song's info view, search results, a song's detail view, and the
 * remove confirmation. Every action re-checks that the presser is the DJ who opened the
 * panel, not only when it was opened.
 * <p>
 * Threading: anything that needs the library (its lock, its files) runs only after the
 * interaction was acknowledged, on {@link YtDlpService#runLibraryWork} (or one of its futures),
 * never on the JDA event thread; its outcome goes through the interaction hook. Checks that need
 * no library (the DJ check, a panel token) still answer with a plain ephemeral reply. Each
 * interaction is acknowledged exactly once.
 *
 * @author Arif Banai (arif-banai)
 */
public class LibraryInteractionListener extends ListenerAdapter {

    /** Shown for a {@code back}/{@code page}/{@code pick}/key-bearing action whose token is missing or expired. */
    static final String EXPIRED_MESSAGE = "This panel has expired; run /library panel again.";
    /** Shown when a panel's song left the library since the panel was rendered. */
    static final String GONE_MESSAGE = "That song is no longer in the library.";

    private final Bot bot;
    private final LibraryPanelTokens tokens;

    public LibraryInteractionListener(Bot bot) {
        this(bot, new LibraryPanelTokens());
    }

    /** For tests: lets a token store with a short TTL and a controllable clock be injected. */
    public LibraryInteractionListener(Bot bot, LibraryPanelTokens tokens) {
        this.bot = bot;
        this.tokens = tokens;
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {
        if (!event.getComponentId().startsWith("lib_")) {
            return;
        }
        handleButton(event);
    }

    @Override
    public void onStringSelectInteraction(@NotNull StringSelectInteractionEvent event) {
        if (!event.getComponentId().startsWith("lib_")) {
            return;
        }
        handlePick(event);
    }

    @Override
    public void onModalInteraction(@NotNull ModalInteractionEvent event) {
        if (!event.getModalId().startsWith("lib_searchmodal_")) {
            return;
        }
        handleSearchModal(event);
    }

    // ---- buttons ----

    private void handleButton(ButtonInteractionEvent event) {
        if (!InteractionGuards.requireGuildAndMember(event)) {
            return;
        }
        Optional<ComponentIdParsers.LibComponentId> parsed = ComponentIdParsers.parseLibComponentId(event.getComponentId());
        if (parsed.isEmpty()) {
            event.reply("Invalid button state.").setEphemeral(true).queue();
            return;
        }
        ComponentIdParsers.LibComponentId id = parsed.get();
        String failure = authorizeFailure(event.getUser().getIdLong(), id.ownerUserId(), event.getGuild(), event.getMember());
        if (failure != null) {
            event.reply(failure).setEphemeral(true).queue();
            return;
        }
        YtDlpService ytDlp = bot.getYtDlp();
        if (ytDlp == null) {
            event.reply("The yt-dlp library isn't available.").setEphemeral(true).queue();
            return;
        }

        switch (id.action()) {
            case "info" -> showInfo(event, ytDlp, id.ownerUserId());
            case "search" -> openSearchModal(event, id.ownerUserId());
            case "rescan" -> doRescan(event, ytDlp, id.ownerUserId());
            case "unlink" -> doUnlink(event, ytDlp, id.ownerUserId());
            case "back" -> showBack(event, ytDlp, id.ownerUserId(), id.arg());
            case "page" -> showSearchPage(event, ytDlp, id.ownerUserId(), id.arg());
            case "play" -> playOrQueue(event, ytDlp, id.ownerUserId(), id.arg(), true);
            case "queue" -> playOrQueue(event, ytDlp, id.ownerUserId(), id.arg(), false);
            case "remove" -> showConfirm(event, ytDlp, id.ownerUserId(), id.arg());
            case "rmidx" -> doRemove(event, ytDlp, id.ownerUserId(), id.arg(), false);
            case "rmfile" -> doRemove(event, ytDlp, id.ownerUserId(), id.arg(), true);
            case "cancel" -> showDetail(event, ytDlp, id.ownerUserId(), id.arg());
            default -> event.reply("Invalid button state.").setEphemeral(true).queue();
        }
    }

    private void showPanel(ButtonInteractionEvent event, YtDlpService ytDlp) {
        long ownerUserId = event.getUser().getIdLong();
        Color color = memberColor(event);
        event.deferEdit().queue();
        ytDlp.libraryStats().whenComplete((stats, ex) -> {
            if (ex != null) {
                event.getHook().sendMessage("Couldn't load the library: " + errorMessage(ex)).setEphemeral(true).queue();
                return;
            }
            event.getHook().editOriginalEmbeds(LibraryPanelSlashCmd.buildPanelEmbed(stats, color, null))
                    .setComponents(LibraryPanelSlashCmd.buildPanelComponents(ownerUserId))
                    .queue();
        });
    }

    private void showInfo(ButtonInteractionEvent event, YtDlpService ytDlp, long ownerUserId) {
        Color color = memberColor(event);
        Guild guild = event.getGuild();
        event.deferEdit().queue();
        ytDlp.runLibraryWork(() -> {
            InfoView view = computeInfoView(guild, ownerUserId, ytDlp, color);
            event.getHook().editOriginalEmbeds(view.embed()).setComponents(view.components()).queue();
        });
    }

    private void doUnlink(ButtonInteractionEvent event, YtDlpService ytDlp, long ownerUserId) {
        Color color = memberColor(event);
        Guild guild = event.getGuild();
        event.deferEdit().queue();
        ytDlp.runLibraryWork(() -> {
            LibraryUnlinkSlashCmd.Result result = LibraryUnlinkSlashCmd.unlinkCurrent(bot, guild);
            InfoView view = computeInfoView(guild, ownerUserId, ytDlp, color);
            event.getHook().editOriginalEmbeds(view.embed()).setComponents(view.components()).queue();
            event.getHook().sendMessage(result.text()).setEphemeral(true).queue();
        });
    }

    private void openSearchModal(ButtonInteractionEvent event, long ownerUserId) {
        TextInput input = TextInput.create("query", TextInputStyle.SHORT)
                .setRequired(true)
                .setPlaceholder("Song title, artist or key")
                .setMaxLength(100)
                .build();
        Modal modal = Modal.create(ComponentIdParsers.libSearchModalId(ownerUserId), "Search the library")
                .addComponents(Label.of("Query", input))
                .build();
        event.replyModal(modal).queue();
    }

    private void doRescan(ButtonInteractionEvent event, YtDlpService ytDlp, long ownerUserId) {
        Color color = memberColor(event);
        event.deferEdit().queue();
        ytDlp.rescanLibrary().whenComplete((summary, rescanEx) -> {
            if (rescanEx != null) {
                event.getHook().sendMessage("Couldn't rescan the library: " + errorMessage(rescanEx)).setEphemeral(true).queue();
                return;
            }
            String note = "Rescanned: " + summary.added() + " added, " + summary.updated() + " updated, "
                    + summary.missing() + " missing.";
            ytDlp.libraryStats().whenComplete((stats, statsEx) -> {
                if (statsEx != null) {
                    event.getHook().sendMessage(note + " (couldn't reload stats: " + errorMessage(statsEx) + ")")
                            .setEphemeral(true).queue();
                    return;
                }
                event.getHook().editOriginalEmbeds(LibraryPanelSlashCmd.buildPanelEmbed(stats, color, note))
                        .setComponents(LibraryPanelSlashCmd.buildPanelComponents(ownerUserId))
                        .queue();
            });
        });
    }

    /** {@code back} with no token goes to the Panel; with one, back to the search list it names. */
    private void showBack(ButtonInteractionEvent event, YtDlpService ytDlp, long ownerUserId, @Nullable String arg) {
        if (arg == null || arg.isBlank()) {
            showPanel(event, ytDlp);
            return;
        }
        Optional<ComponentIdParsers.LibTokenArg> parsed = ComponentIdParsers.parseLibTokenArg(arg);
        Optional<LibraryPanelTokens.SearchState> state = parsed.flatMap(p -> ownedState(p.token(), ownerUserId));
        if (state.isEmpty()) {
            event.reply(EXPIRED_MESSAGE).setEphemeral(true).queue();
            return;
        }
        editWithSearchList(event, ytDlp, ownerUserId, state.get().query(), state.get().page(), memberColor(event));
    }

    private void showSearchPage(ButtonInteractionEvent event, YtDlpService ytDlp, long ownerUserId, @Nullable String arg) {
        Optional<ComponentIdParsers.LibTokenArg> parsed = ComponentIdParsers.parseLibTokenArg(arg);
        Optional<LibraryPanelTokens.SearchState> state = parsed.isEmpty() || parsed.get().index() == null
                ? Optional.empty() : ownedState(parsed.get().token(), ownerUserId);
        if (state.isEmpty()) {
            event.reply(EXPIRED_MESSAGE).setEphemeral(true).queue();
            return;
        }
        int targetPage = state.get().page() + parsed.get().index();
        editWithSearchList(event, ytDlp, ownerUserId, state.get().query(), targetPage, memberColor(event));
    }

    /** Defers {@code event}, then searches and renders the list off the event thread, through the hook. */
    private void editWithSearchList(IMessageEditCallback event, YtDlpService ytDlp, long ownerUserId, String query,
                                    int page, Color color) {
        event.deferEdit().queue();
        ytDlp.runLibraryWork(() -> {
            SearchListView view = computeSearchListView(ytDlp, ownerUserId, query, page, color);
            event.getHook().editOriginalEmbeds(view.embed()).setComponents(view.components()).queue();
        });
    }

    private void playOrQueue(ButtonInteractionEvent event, YtDlpService ytDlp, long ownerUserId, @Nullable String arg,
                             boolean playNow) {
        KeyLookup lookup = lookupKey(arg, ownerUserId);
        if (lookup.failure() != null) {
            event.reply(lookup.failure()).setEphemeral(true).queue();
            return;
        }
        if (!InteractionGuards.ensureBotInUserVoiceChannel(event, bot)) {
            return;
        }
        GuildMessageChannel channel = event.getChannel().asGuildMessageChannel();
        MusicService.OutputAdapter output = OutputAdapters.forLibraryReply(event);
        Guild guild = event.getGuild();
        Member member = event.getMember();
        event.deferEdit().queue();
        ytDlp.runLibraryWork(() -> {
            Optional<LibraryEntry> entry = ytDlp.library().find(lookup.key());
            if (entry.isEmpty()) {
                event.getHook().sendMessage(GONE_MESSAGE).setEphemeral(true).queue();
                return;
            }
            String path = entry.get().file().toAbsolutePath().toString();
            MusicService musicService = bot.getMusicService();
            if (playNow) {
                musicService.playNowFromUrl(guild, member, path, channel, output);
            } else {
                musicService.play(guild, member, path, channel, output);
            }
        });
    }

    private void showConfirm(ButtonInteractionEvent event, YtDlpService ytDlp, long ownerUserId, @Nullable String arg) {
        KeyLookup lookup = lookupKey(arg, ownerUserId);
        if (lookup.failure() != null) {
            event.reply(lookup.failure()).setEphemeral(true).queue();
            return;
        }
        ComponentIdParsers.LibTokenArg parsed = ComponentIdParsers.parseLibTokenArg(arg).orElseThrow();
        Color color = memberColor(event);
        event.deferEdit().queue();
        ytDlp.runLibraryWork(() -> {
            Optional<LibraryEntry> entry = ytDlp.library().find(lookup.key());
            if (entry.isEmpty()) {
                event.getHook().sendMessage(GONE_MESSAGE).setEphemeral(true).queue();
                return;
            }
            event.getHook().editOriginalEmbeds(LibraryPanelSlashCmd.buildConfirmEmbed(entry.get(), color))
                    .setComponents(LibraryPanelSlashCmd.buildConfirmComponents(ownerUserId, parsed.token(), parsed.index()))
                    .queue();
        });
    }

    private void showDetail(ButtonInteractionEvent event, YtDlpService ytDlp, long ownerUserId, @Nullable String arg) {
        editWithDetail(event, ytDlp, ownerUserId, arg, memberColor(event));
    }

    /**
     * Resolves {@code arg} (a {@code token_index}) and, if the token is still good, defers
     * {@code event} and renders that song's detail view off the event thread, through the hook.
     */
    private <E extends IMessageEditCallback & IReplyCallback> void editWithDetail(E event, YtDlpService ytDlp,
                                                                                 long ownerUserId, @Nullable String arg,
                                                                                 Color color) {
        KeyLookup lookup = lookupKey(arg, ownerUserId);
        if (lookup.failure() != null) {
            event.reply(lookup.failure()).setEphemeral(true).queue();
            return;
        }
        ComponentIdParsers.LibTokenArg parsed = ComponentIdParsers.parseLibTokenArg(arg).orElseThrow();
        event.deferEdit().queue();
        ytDlp.runLibraryWork(() -> {
            Optional<LibraryEntry> entry = ytDlp.library().find(lookup.key());
            if (entry.isEmpty()) {
                event.getHook().sendMessage(GONE_MESSAGE).setEphemeral(true).queue();
                return;
            }
            DetailView view = buildDetailView(entry.get(), ownerUserId, parsed.token(), parsed.index(), color);
            event.getHook().editOriginalEmbeds(view.embed()).setComponents(view.components()).queue();
        });
    }

    private void doRemove(ButtonInteractionEvent event, YtDlpService ytDlp, long ownerUserId, @Nullable String arg,
                          boolean deleteFile) {
        KeyLookup lookup = lookupKey(arg, ownerUserId);
        if (lookup.failure() != null) {
            event.reply(lookup.failure()).setEphemeral(true).queue();
            return;
        }
        String key = lookup.key();
        long actorUserId = event.getUser().getIdLong();
        event.deferEdit().queue();
        ytDlp.removeFromLibrary(key, deleteFile, actorUserId).whenComplete((result, ex) -> {
            String content;
            if (ex != null) {
                content = "Couldn't remove that song: " + errorMessage(ex);
            } else if (result.removed()) {
                content = "Removed from the library" + (result.fileDeleted() ? " and deleted the file." : ".");
            } else {
                content = "Couldn't remove that song: " + (result.problem() != null ? result.problem() : "unknown error");
            }
            event.getHook().editOriginal(content).setEmbeds(java.util.Collections.emptyList())
                    .setComponents(LibraryPanelSlashCmd.buildBackOnlyComponents(ownerUserId))
                    .queue();
        });
    }

    // ---- select menu (search result pick) ----

    private void handlePick(StringSelectInteractionEvent event) {
        if (!InteractionGuards.requireGuildAndMember(event)) {
            return;
        }
        Optional<ComponentIdParsers.LibComponentId> parsed = ComponentIdParsers.parseLibComponentId(event.getComponentId());
        if (parsed.isEmpty() || !"pick".equals(parsed.get().action())) {
            event.reply("Invalid selection state.").setEphemeral(true).queue();
            return;
        }
        ComponentIdParsers.LibComponentId id = parsed.get();
        String failure = authorizeFailure(event.getUser().getIdLong(), id.ownerUserId(), event.getGuild(), event.getMember());
        if (failure != null) {
            event.reply(failure).setEphemeral(true).queue();
            return;
        }
        YtDlpService ytDlp = bot.getYtDlp();
        if (ytDlp == null) {
            event.reply("The yt-dlp library isn't available.").setEphemeral(true).queue();
            return;
        }
        String token = id.arg();
        if (token == null || token.isBlank() || event.getValues().isEmpty()) {
            event.reply(EXPIRED_MESSAGE).setEphemeral(true).queue();
            return;
        }
        String arg;
        try {
            arg = token + "_" + Integer.parseInt(event.getValues().get(0));
        } catch (NumberFormatException e) {
            event.reply(EXPIRED_MESSAGE).setEphemeral(true).queue();
            return;
        }
        Color color = event.getMember() == null ? null : event.getMember().getColor();
        editWithDetail(event, ytDlp, id.ownerUserId(), arg, color);
    }

    // ---- modal (search query) ----

    private void handleSearchModal(ModalInteractionEvent event) {
        if (!InteractionGuards.requireGuildAndMember(event)) {
            return;
        }
        Optional<Long> ownerUserId = ComponentIdParsers.parseLibSearchModalOwnerId(event.getModalId());
        if (ownerUserId.isEmpty()) {
            event.reply("Invalid search state.").setEphemeral(true).queue();
            return;
        }
        String failure = authorizeFailure(event.getUser().getIdLong(), ownerUserId.get(), event.getGuild(), event.getMember());
        if (failure != null) {
            event.reply(failure).setEphemeral(true).queue();
            return;
        }
        YtDlpService ytDlp = bot.getYtDlp();
        if (ytDlp == null) {
            event.reply("The yt-dlp library isn't available.").setEphemeral(true).queue();
            return;
        }
        String query = modalValue(event, "query");
        if (query.isBlank()) {
            event.reply("Please enter something to search for.").setEphemeral(true).queue();
            return;
        }
        Color color = event.getMember() == null ? null : event.getMember().getColor();
        editWithSearchList(event, ytDlp, ownerUserId.get(), query, 1, color);
    }

    // ---- shared view builders ----

    private record InfoView(MessageEmbed embed, List<ActionRow> components) {
    }

    private record DetailView(MessageEmbed embed, List<ActionRow> components) {
    }

    private record SearchListView(MessageEmbed embed, List<ActionRow> components) {
    }

    private InfoView computeInfoView(Guild guild, long ownerUserId, YtDlpService ytDlp, Color color) {
        AudioTrack playing = guild.getAudioManager().getSendingHandler() instanceof AudioHandler handler
                ? handler.getPlayer().getPlayingTrack() : null;
        LibraryEntry entry = null;
        boolean showUnlink = false;
        List<String> aliases = List.of();
        if (playing != null) {
            String key = LibraryKeys.of(playing).orElse(null);
            if (key != null) {
                entry = ytDlp.library().find(key).orElse(null);
                if (entry != null) {
                    aliases = ytDlp.aliasesTo(key);
                    showUnlink = playing instanceof LibraryAudioTrack libraryTrack && libraryTrack.getLinkedFrom() != null;
                }
            }
        }
        return new InfoView(LibraryPanelSlashCmd.buildInfoEmbed(entry, aliases, color),
                LibraryPanelSlashCmd.buildInfoComponents(ownerUserId, showUnlink));
    }

    private DetailView buildDetailView(LibraryEntry entry, long ownerUserId, String token, int index, Color color) {
        return new DetailView(LibraryPanelSlashCmd.buildDetailEmbed(entry, color),
                LibraryPanelSlashCmd.buildDetailComponents(ownerUserId, token, index));
    }

    /**
     * Searches, pages, mints a fresh {@link LibraryPanelTokens} token for the page actually
     * rendered (so its select menu and Prev/Next resolve against this exact list), and builds
     * the view.
     */
    private SearchListView computeSearchListView(YtDlpService ytDlp, long ownerUserId, String query, int page, Color color) {
        List<LibraryEntry> results = ytDlp.searchLibrary(query, LibraryPanelSlashCmd.searchLimit());
        int totalPages = LibraryPanelSlashCmd.totalPages(results.size());
        int safePage = Math.max(1, Math.min(page, totalPages));
        List<LibraryEntry> pageEntries = LibraryPanelSlashCmd.pageOf(results, safePage);
        List<String> keys = pageEntries.stream().map(LibraryEntry::key).toList();
        String token = tokens.create(ownerUserId, query, safePage, keys);
        return new SearchListView(
                LibraryPanelSlashCmd.buildSearchListEmbed(query, pageEntries, safePage, totalPages, results.size(), color),
                LibraryPanelSlashCmd.buildSearchListComponents(ownerUserId, token, pageEntries, safePage, totalPages));
    }

    // ---- helpers ----

    /** Null if {@code presserId} is the DJ who opened the panel; otherwise the ephemeral message to reply with. */
    private @Nullable String authorizeFailure(long presserId, long ownerUserId, Guild guild, Member member) {
        if (presserId != ownerUserId) {
            return "This panel belongs to someone else.";
        }
        if (!DJCommand.checkDJPermission(bot, guild, member)) {
            return "Only DJs can manage the library.";
        }
        return null;
    }

    /** What resolving a {@code token_index} component-ID argument to a library key found: the key, or why not. */
    private record KeyLookup(@Nullable String key, @Nullable String failure) {
    }

    /**
     * Resolves a {@code token_index} argument to a library key via {@link #tokens}. Touches no
     * library state, so it can run (and reply) on the event thread: whether the key is still in the
     * library is checked after the interaction was deferred. A token minted for another user's
     * panel counts as expired.
     */
    private KeyLookup lookupKey(@Nullable String arg, long ownerUserId) {
        Optional<ComponentIdParsers.LibTokenArg> parsed = ComponentIdParsers.parseLibTokenArg(arg);
        if (parsed.isEmpty() || parsed.get().index() == null) {
            return new KeyLookup(null, EXPIRED_MESSAGE);
        }
        Optional<LibraryPanelTokens.SearchState> state = ownedState(parsed.get().token(), ownerUserId);
        if (state.isEmpty()) {
            return new KeyLookup(null, EXPIRED_MESSAGE);
        }
        int index = parsed.get().index();
        List<String> keys = state.get().keys();
        if (index < 0 || index >= keys.size()) {
            return new KeyLookup(null, EXPIRED_MESSAGE);
        }
        return new KeyLookup(keys.get(index), null);
    }

    /** The token's state, unless it's missing, expired, or was minted for another user's panel. */
    private Optional<LibraryPanelTokens.SearchState> ownedState(String token, long ownerUserId) {
        return tokens.get(token).filter(state -> state.ownerUserId() == ownerUserId);
    }

    private static Color memberColor(ButtonInteractionEvent event) {
        return event.getMember() == null ? null : event.getMember().getColor();
    }

    private static String modalValue(ModalInteractionEvent event, String inputId) {
        return event.getValues().stream()
                .filter(m -> inputId.equals(m.getCustomId()))
                .findFirst()
                .map(net.dv8tion.jda.api.interactions.modals.ModalMapping::getAsString)
                .orElse("")
                .trim();
    }

    private static String errorMessage(Throwable ex) {
        return ex.getMessage() != null ? ex.getMessage() : ex.toString();
    }
}
