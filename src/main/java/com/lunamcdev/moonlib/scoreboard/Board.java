package com.lunamcdev.moonlib.scoreboard;

import com.lunamcdev.moonlib.util.Text;
import com.lunamcdev.moonlib.version.Reflection;
import com.lunamcdev.moonlib.version.ServerVersion;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A flicker-free sidebar scoreboard for a single player.
 *
 * <p>Each line is rendered through a team prefix/suffix on a fixed invisible entry, so updating a line
 * only re-sends that line's team: no flicker and no duplicated lines, on every version. Lines are only
 * re-sent when their text actually changes, which makes frequent updates cheap.</p>
 *
 * <pre>{@code
 * Board board = Board.create(player);
 * board.title("&d&lMOON PRISON");
 * board.lines(
 *         "&7" + date,
 *         "",
 *         "&fRank: &d" + rank,
 *         "&fBalance: &a$" + Numbers.compact(balance),
 *         "",
 *         "&dplay.moonprison.net");
 * // later
 * board.line(3, "&fBalance: &a$" + Numbers.compact(newBalance));
 * board.delete();
 * }</pre>
 *
 * <h2>Limits</h2>
 * <ul>
 *     <li>Up to {@value #MAX_LINES} lines.</li>
 *     <li>Before 1.13 each line can be up to 32 visible characters (16 prefix + 16 suffix, colour codes
 *     included) and the title 32 characters. From 1.13 lines can be up to 128 characters and the title
 *     128. Longer text is trimmed safely, never splitting a colour code.</li>
 *     <li>On Paper 1.20.3+ the red score numbers are hidden automatically.</li>
 * </ul>
 *
 * <p>For automatic per-player boards that update on a timer, use {@link BoardManager}.</p>
 */
public final class Board {

    /** The maximum number of lines a sidebar can show. */
    public static final int MAX_LINES = 15;

    private static final ChatColor[] ENTRY_COLORS = Arrays.copyOfRange(ChatColor.values(), 0, 16);

    private final Player player;
    private final Scoreboard scoreboard;
    private final Objective objective;
    private final List<String> lines = new ArrayList<>();
    private final int partLimit;
    private final int titleLimit;
    private String title = "";
    private boolean deleted;

    private Board(Player player) {
        this.player = player;
        this.scoreboard = Bukkit.getScoreboardManager().getNewScoreboard();
        boolean modern = ServerVersion.current().isAtLeast(ServerVersion.V1_13);
        this.partLimit = modern ? 64 : 16;
        this.titleLimit = modern ? 128 : 32;
        this.objective = registerObjective(scoreboard, modern);
        this.objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        hideNumbers(objective);
        player.setScoreboard(scoreboard);
    }

    /**
     * Creates a board and shows it to the player, replacing their current scoreboard.
     *
     * @param player the player
     * @return the board
     */
    public static Board create(Player player) {
        return new Board(player);
    }

    /**
     * Sets the title. Colour codes are supported.
     *
     * @param newTitle the title
     */
    public void title(String newTitle) {
        checkNotDeleted();
        String coloured = trim(Text.color(newTitle), titleLimit);
        if (!coloured.equals(title)) {
            title = coloured;
            objective.setDisplayName(coloured);
        }
    }

    /**
     * Replaces all lines. Lines beyond {@value #MAX_LINES} are ignored.
     *
     * @param newLines the lines, top to bottom
     */
    public void lines(String... newLines) {
        lines(Arrays.asList(newLines));
    }

    /**
     * Replaces all lines. Lines beyond {@value #MAX_LINES} are ignored.
     *
     * @param newLines the lines, top to bottom
     */
    public void lines(List<String> newLines) {
        checkNotDeleted();
        int size = Math.min(newLines.size(), MAX_LINES);
        boolean sizeChanged = size != lines.size();

        // Remove lines that no longer exist.
        for (int i = lines.size() - 1; i >= size; i--) {
            removeLine(i);
        }
        for (int i = 0; i < size; i++) {
            String text = Text.color(newLines.get(i));
            if (i >= lines.size()) {
                lines.add(null);
            }
            if (!text.equals(lines.get(i))) {
                lines.set(i, text);
                renderText(i, text);
            }
            if (sizeChanged) {
                objective.getScore(entry(i)).setScore(size - i);
            }
        }
    }

    /**
     * Updates a single existing line.
     *
     * @param index the zero-based line, from the top
     * @param text  the new text
     */
    public void line(int index, String text) {
        checkNotDeleted();
        if (index < 0 || index >= lines.size()) {
            throw new IndexOutOfBoundsException("Line " + index + " does not exist (board has " + lines.size() + ")");
        }
        String coloured = Text.color(text);
        if (!coloured.equals(lines.get(index))) {
            lines.set(index, coloured);
            renderText(index, coloured);
        }
    }

    /**
     * Returns the current lines (coloured).
     *
     * @return an unmodifiable copy
     */
    public List<String> lines() {
        return Collections.unmodifiableList(new ArrayList<>(lines));
    }

    /**
     * Returns the number of lines.
     *
     * @return the line count
     */
    public int size() {
        return lines.size();
    }

    /**
     * Returns the player this board belongs to.
     *
     * @return the player
     */
    public Player player() {
        return player;
    }

    /**
     * Returns the underlying Bukkit scoreboard, e.g. to add name-tag teams.
     *
     * @return the scoreboard
     */
    public Scoreboard scoreboard() {
        return scoreboard;
    }

    /**
     * Returns whether {@link #delete()} has been called.
     *
     * @return {@code true} if deleted
     */
    public boolean isDeleted() {
        return deleted;
    }

    /** Removes the board and restores the server's main scoreboard for the player. */
    public void delete() {
        if (deleted) {
            return;
        }
        deleted = true;
        try {
            objective.unregister();
        } catch (IllegalStateException ignored) {
            // already unregistered
        }
        if (player.isOnline() && player.getScoreboard() == scoreboard) {
            player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
        }
    }

    private void renderText(int index, String text) {
        Team team = team(index);
        String prefix;
        String suffix = "";
        if (text.length() <= partLimit) {
            prefix = text;
        } else {
            int cut = partLimit;
            if (text.charAt(cut - 1) == ChatColor.COLOR_CHAR) {
                cut--; // never split "§a" across prefix and suffix
            }
            cut = avoidSplittingHex(text, cut);
            prefix = text.substring(0, cut);
            String carried = ChatColor.getLastColors(prefix);
            suffix = trim(carried + text.substring(cut), partLimit);
        }
        team.setPrefix(prefix);
        team.setSuffix(suffix);
    }

    private void removeLine(int index) {
        String entry = entry(index);
        scoreboard.resetScores(entry);
        Team team = scoreboard.getTeam(teamName(index));
        if (team != null) {
            team.unregister();
        }
        lines.remove(index);
    }

    private Team team(int index) {
        String name = teamName(index);
        Team team = scoreboard.getTeam(name);
        if (team == null) {
            team = scoreboard.registerNewTeam(name);
            team.addEntry(entry(index));
        }
        return team;
    }

    private static String teamName(int index) {
        return "moonlib_" + index;
    }

    private static String entry(int index) {
        return ENTRY_COLORS[index].toString() + ChatColor.RESET;
    }

    private void checkNotDeleted() {
        if (deleted) {
            throw new IllegalStateException("This board has been deleted");
        }
    }

    private static String trim(String text, int limit) {
        if (text.length() <= limit) {
            return text;
        }
        int cut = limit;
        if (text.charAt(cut - 1) == ChatColor.COLOR_CHAR) {
            cut--;
        }
        return text.substring(0, avoidSplittingHex(text, cut));
    }

    /** Moves a cut point before a §x§R§R§G§G§B§B hex sequence if it would split one. */
    private static int avoidSplittingHex(String text, int cut) {
        int start = Math.max(0, cut - 13);
        for (int i = start; i < cut; i++) {
            if (text.charAt(i) == ChatColor.COLOR_CHAR && i + 1 < text.length()
                    && (text.charAt(i + 1) == 'x' || text.charAt(i + 1) == 'X') && i + 14 > cut) {
                return i;
            }
        }
        return cut;
    }

    @SuppressWarnings("deprecation")
    private static Objective registerObjective(Scoreboard scoreboard, boolean modern) {
        if (modern) {
            return scoreboard.registerNewObjective("moonlib", "dummy", "");
        }
        return scoreboard.registerNewObjective("moonlib", "dummy");
    }

    /** Paper 1.20.3+: objective.numberFormat(NumberFormat.blank()), via reflection. */
    private static void hideNumbers(Objective objective) {
        Class<?> numberFormat = Reflection.findClass("io.papermc.paper.scoreboard.numbers.NumberFormat");
        if (numberFormat == null) {
            return;
        }
        try {
            Object blank = numberFormat.getMethod("blank").invoke(null);
            Method setter = Objective.class.getMethod("numberFormat", numberFormat);
            setter.invoke(objective, blank);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // not supported on this build
        }
    }
}
