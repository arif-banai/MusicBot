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
package com.jagrosh.jmusicbot.commands.v2.dj;

import java.awt.Color;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.v2.DJSlashCommand;
import com.jagrosh.jmusicbot.commands.v2.music.PlaylistsSlashCmd;
import com.jagrosh.jmusicbot.listener.interaction.ComponentIdParsers;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.PaginatedListEmbedUtil;
import com.jagrosh.jmusicbot.ytdlp.LibraryEntry;
import com.jagrosh.jmusicbot.ytdlp.YtDlpLibrary;
import com.jagrosh.jmusicbot.ytdlp.YtDlpService;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.components.selections.StringSelectMenu;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.jetbrains.annotations.Nullable;

/**
 * {@code /library panel} (DJs only): an ephemeral panel for browsing and managing the yt-dlp
 * library: info on the current song, search, remove and rescan. Registered alongside
 * {@code /library unlink}, so only when yt-dlp is configured.
 * <p>
 * This class also holds the static view builders the panel's interactions re-render with
 * ({@link com.jagrosh.jmusicbot.listener.LibraryInteractionListener}), the same way
 * {@link PlaylistsSlashCmd} holds the Playlists panel's.
 *
 * @author Arif Banai (arif-banai)
 */
public class LibraryPanelSlashCmd extends DJSlashCommand
{
    /** Search results per page, matching the Playlists panel's track page size. */
    public static final int PAGE_SIZE = PlaylistsSlashCmd.PLAYLIST_TRACKS_PER_PAGE;
    /** The most matches ever fetched from the library for one query, spread over pages. */
    private static final int MAX_SEARCH_RESULTS = 100;

    public LibraryPanelSlashCmd(Bot bot)
    {
        super(bot);
        this.name = "panel";
        this.help = "opens the yt-dlp library management panel";
        this.bePlaying = false;
    }

    @Override
    public void doDJCommand(SlashCommandEvent event)
    {
        YtDlpService ytDlp = bot.getYtDlp();
        if (ytDlp == null)
        {
            event.reply("The yt-dlp library isn't available.").setEphemeral(true).queue();
            return;
        }
        long ownerUserId = event.getUser().getIdLong();
        Color color = event.getMember() == null ? null : event.getMember().getColor();
        event.deferReply(true).queue(hook -> ytDlp.libraryStats().whenComplete((stats, ex) ->
        {
            if (ex != null)
            {
                hook.sendMessage("Couldn't load the library: " + errorMessage(ex)).setEphemeral(true).queue();
                return;
            }
            hook.sendMessageEmbeds(buildPanelEmbed(stats, color, null))
                    .setComponents(buildPanelComponents(ownerUserId))
                    .queue();
        }));
    }

    // ---- Panel view ----

    public static MessageEmbed buildPanelEmbed(YtDlpLibrary.LibraryStats stats, Color color, @Nullable String note)
    {
        StringBuilder description = new StringBuilder();
        if (note != null && !note.isBlank())
        {
            description.append(note).append("\n\n");
        }
        description.append("**Songs:** ").append(stats.songs()).append('\n')
                .append("**Size:** ").append(formatBytes(stats.totalBytes())).append('\n')
                .append("**Folder:** `").append(stats.folder()).append('`');
        EmbedBuilder embed = new EmbedBuilder().setTitle("Library").setDescription(description.toString());
        PaginatedListEmbedUtil.applyStandardEmbedOptions(embed, null, color);
        return embed.build();
    }

    public static List<ActionRow> buildPanelComponents(long ownerUserId)
    {
        Button info = Button.primary(ComponentIdParsers.libComponentId("info", ownerUserId), "Info");
        Button search = Button.secondary(ComponentIdParsers.libComponentId("search", ownerUserId), "Search");
        Button rescan = Button.secondary(ComponentIdParsers.libComponentId("rescan", ownerUserId), "Rescan");
        return List.of(ActionRow.of(info, search, rescan));
    }

    // ---- Info view ----

    public static MessageEmbed buildInfoEmbed(@Nullable LibraryEntry entry, List<String> aliases, Color color)
    {
        EmbedBuilder embed = new EmbedBuilder().setTitle("Library Info");
        if (entry == null)
        {
            embed.setDescription("The current song isn't in the library.");
        }
        else
        {
            // The sidecar's artist when known; the uploader (often a label or lyrics channel) only besides it.
            boolean hasArtist = entry.artist() != null && !entry.artist().isBlank();
            String artist = hasArtist ? entry.artist() : entry.author();
            embed.setDescription(truncate("**" + FormatUtil.filter(entry.title()) + "**", MessageEmbed.DESCRIPTION_MAX_LENGTH))
                    .addField("Artist", artist.isEmpty() ? "*unknown*" : field(FormatUtil.filter(artist)), true);
            if (hasArtist && !entry.author().isEmpty() && !entry.author().equalsIgnoreCase(entry.artist()))
            {
                embed.addField("Uploader", field(FormatUtil.filter(entry.author())), true);
            }
            embed.addField("Source", entry.source() == null ? "file" : field(entry.source()), true)
                    .addField("Key", field("`" + entry.key() + "`"), true)
                    .addField("File", field("`" + fileName(entry) + "`"), true)
                    .addField("Size", formatBytes(fileSize(entry.file())), true)
                    .addField("Aliases", aliasList(aliases), false)
                    .addField("Link", field(entry.uri()), false);
        }
        PaginatedListEmbedUtil.applyStandardEmbedOptions(embed, null, color);
        return embed.build();
    }

    public static List<ActionRow> buildInfoComponents(long ownerUserId, boolean showUnlink)
    {
        List<Button> buttons = new ArrayList<>();
        if (showUnlink)
        {
            buttons.add(Button.danger(ComponentIdParsers.libComponentId("unlink", ownerUserId), "Unlink"));
        }
        buttons.add(Button.secondary(ComponentIdParsers.libComponentId("back", ownerUserId), "Back"));
        return List.of(ActionRow.of(buttons));
    }

    // ---- Search results view ----

    public static MessageEmbed buildSearchListEmbed(String query, List<LibraryEntry> pageEntries, int page,
                                                     int totalPages, int totalResults, Color color)
    {
        List<String> lines = pageEntries.stream()
                .map(e -> "`" + FormatUtil.filter(e.title())
                        + (e.author().isEmpty() ? "" : " — " + FormatUtil.filter(e.author())) + "`")
                .toList();
        String description = PaginatedListEmbedUtil.buildNumberedListSection(
                "**Results for \"" + FormatUtil.filter(query) + "\"** *(select one below)*",
                lines, 0, (page - 1) * PAGE_SIZE + 1);
        EmbedBuilder embed = new EmbedBuilder().setTitle("Library Search").setDescription(description)
                .addField("Matches", String.valueOf(totalResults), true);
        PaginatedListEmbedUtil.applyStandardEmbedOptions(embed, "Page " + page + " of " + totalPages, color);
        return embed.build();
    }

    /**
     * @param token the {@link com.jagrosh.jmusicbot.listener.interaction.LibraryPanelTokens} token
     *              for this render: {@code pageEntries}, in order, is exactly what that token's
     *              {@code keys()} holds, so a select option's value (its 0-based index here) and a
     *              detail/confirm button's index both resolve against the same list.
     */
    public static List<ActionRow> buildSearchListComponents(long ownerUserId, String token,
                                                             List<LibraryEntry> pageEntries, int page, int totalPages)
    {
        List<ActionRow> rows = new ArrayList<>();
        if (!pageEntries.isEmpty())
        {
            StringSelectMenu.Builder menu = StringSelectMenu.create(ComponentIdParsers.libComponentId("pick", ownerUserId, token))
                    .setPlaceholder("Select a song")
                    .setMinValues(1)
                    .setMaxValues(1);
            for (int i = 0; i < pageEntries.size(); i++)
            {
                LibraryEntry entry = pageEntries.get(i);
                String label = truncate(entry.title(), 100);
                // The option's value is the index into pageEntries/token's keys, never the key
                // itself: a file: or soundcloud: key can exceed Discord's own 100-char value limit.
                if (entry.author().isEmpty())
                {
                    menu.addOption(label, String.valueOf(i)); // no description at all, rather than ""
                }
                else
                {
                    menu.addOption(label, String.valueOf(i), truncate(entry.author(), 100));
                }
            }
            rows.add(ActionRow.of(menu.build()));
        }
        Button back = Button.secondary(ComponentIdParsers.libComponentId("back", ownerUserId), "Back");
        if (totalPages > 1)
        {
            Button prev = Button.secondary(ComponentIdParsers.libComponentId("page", ownerUserId, token, -1), "Prev");
            Button next = Button.secondary(ComponentIdParsers.libComponentId("page", ownerUserId, token, 1), "Next");
            if (page <= 1)
            {
                prev = prev.asDisabled();
            }
            if (page >= totalPages)
            {
                next = next.asDisabled();
            }
            rows.add(ActionRow.of(prev, next, back));
        }
        else
        {
            rows.add(ActionRow.of(back));
        }
        return rows;
    }

    /** Clamps {@code limit} to {@link #MAX_SEARCH_RESULTS} for a library search. */
    public static int searchLimit()
    {
        return MAX_SEARCH_RESULTS;
    }

    public static int totalPages(int totalResults)
    {
        return Math.max(1, (int) Math.ceil(totalResults / (double) PAGE_SIZE));
    }

    /** The entries on {@code page} (1-based) of {@code results}, clamping an out-of-range page. */
    public static List<LibraryEntry> pageOf(List<LibraryEntry> results, int page)
    {
        int totalPages = totalPages(results.size());
        int safePage = Math.max(1, Math.min(page, totalPages));
        int from = (safePage - 1) * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, results.size());
        return from >= to ? List.of() : results.subList(from, to);
    }

    // ---- Detail view ----

    public static MessageEmbed buildDetailEmbed(LibraryEntry entry, Color color)
    {
        EmbedBuilder embed = new EmbedBuilder().setTitle(truncate(FormatUtil.filter(entry.title()), MessageEmbed.TITLE_MAX_LENGTH))
                .addField("Author", entry.author().isEmpty() ? "*unknown*" : field(FormatUtil.filter(entry.author())), true)
                .addField("Source", entry.source() == null ? "file" : field(entry.source()), true)
                .addField("Key", field("`" + entry.key() + "`"), true)
                .addField("File", field("`" + fileName(entry) + "`"), true)
                .addField("Size", formatBytes(fileSize(entry.file())), true)
                .addField("Link", field(entry.uri()), false);
        PaginatedListEmbedUtil.applyStandardEmbedOptions(embed, null, color);
        return embed.build();
    }

    /**
     * @param token the search render's token; {@code index} is this song's position in that
     *              token's key list. Back returns to that same search page ({@code token} alone,
     *              with no index, means "the search list this token was rendered for").
     */
    public static List<ActionRow> buildDetailComponents(long ownerUserId, String token, int index)
    {
        Button play = Button.success(ComponentIdParsers.libComponentId("play", ownerUserId, token, index), "Play");
        Button queue = Button.primary(ComponentIdParsers.libComponentId("queue", ownerUserId, token, index), "Queue");
        Button remove = Button.danger(ComponentIdParsers.libComponentId("remove", ownerUserId, token, index), "Remove…");
        Button back = Button.secondary(ComponentIdParsers.libComponentId("back", ownerUserId, token), "Back");
        return List.of(ActionRow.of(play, queue, remove, back));
    }

    // ---- Remove confirmation view ----

    public static MessageEmbed buildConfirmEmbed(LibraryEntry entry, Color color)
    {
        EmbedBuilder embed = new EmbedBuilder().setTitle("Remove from library?")
                .setDescription(truncate("Remove **" + FormatUtil.filter(entry.title()) + "** from the library?",
                        MessageEmbed.DESCRIPTION_MAX_LENGTH));
        PaginatedListEmbedUtil.applyStandardEmbedOptions(embed, null, color);
        return embed.build();
    }

    public static List<ActionRow> buildConfirmComponents(long ownerUserId, String token, int index)
    {
        Button rmidx = Button.danger(ComponentIdParsers.libComponentId("rmidx", ownerUserId, token, index), "Remove from library");
        Button rmfile = Button.danger(ComponentIdParsers.libComponentId("rmfile", ownerUserId, token, index), "Remove and delete file");
        Button cancel = Button.secondary(ComponentIdParsers.libComponentId("cancel", ownerUserId, token, index), "Cancel");
        return List.of(ActionRow.of(rmidx, rmfile, cancel));
    }

    // ---- Shared helpers ----

    public static List<ActionRow> buildBackOnlyComponents(long ownerUserId)
    {
        return List.of(ActionRow.of(Button.secondary(ComponentIdParsers.libComponentId("back", ownerUserId), "Back")));
    }

    private static String fileName(LibraryEntry entry)
    {
        return entry.file().getFileName() == null ? entry.file().toString() : entry.file().getFileName().toString();
    }

    static long fileSize(Path file)
    {
        try
        {
            return Files.size(file);
        }
        catch (IOException ex)
        {
            return 0L;
        }
    }

    static String formatBytes(long bytes)
    {
        if (bytes < 1024)
        {
            return bytes + " B";
        }
        String[] units = { "KB", "MB", "GB", "TB" };
        double value = bytes;
        int unit = -1;
        do
        {
            value /= 1024.0;
            unit++;
        }
        while (value >= 1024.0 && unit < units.length - 1);
        return String.format("%.1f %s", value, units[unit]);
    }

    /** {@code text} cut to fit an embed field's value (1024 characters). */
    static String field(String text)
    {
        return truncate(text, MessageEmbed.VALUE_MAX_LENGTH);
    }

    /**
     * The aliases as a list of keys, as many as fit in one embed field, then how many more there
     * are; "none" for none.
     */
    static String aliasList(List<String> aliases)
    {
        if (aliases.isEmpty())
        {
            return "none";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < aliases.size(); i++)
        {
            String item = (i == 0 ? "" : ", ") + "`" + aliases.get(i) + "`";
            int remaining = aliases.size() - i;
            String more = " (+" + remaining + " more)";
            if (sb.length() + item.length() + (remaining > 1 ? more.length() : 0) > MessageEmbed.VALUE_MAX_LENGTH)
            {
                if (sb.length() == 0)
                {
                    // Even the first key alone is too long: cut it, and count only the others.
                    String others = aliases.size() > 1 ? " (+" + (aliases.size() - 1) + " more)" : "";
                    return truncate("`" + aliases.get(0) + "`", MessageEmbed.VALUE_MAX_LENGTH - others.length()) + others;
                }
                return sb.append(more).toString();
            }
            sb.append(item);
        }
        return sb.toString();
    }

    static String truncate(String text, int max)
    {
        if (text == null)
        {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }

    private static String errorMessage(Throwable ex)
    {
        return ex.getMessage() != null ? ex.getMessage() : ex.toString();
    }
}
