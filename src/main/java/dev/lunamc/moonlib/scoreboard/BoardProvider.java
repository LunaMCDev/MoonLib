package dev.lunamc.moonlib.scoreboard;

import org.bukkit.entity.Player;

import java.util.List;

/**
 * Supplies the title and lines of each player's sidebar for a {@link BoardManager}.
 *
 * <pre>{@code
 * public final class PrisonBoard implements BoardProvider {
 *     @Override
 *     public String title(Player player) {
 *         return "&d&lMOON PRISON";
 *     }
 *
 *     @Override
 *     public List<String> lines(Player player) {
 *         PrisonPlayer data = players.get(player);
 *         return Arrays.asList(
 *                 "",
 *                 "&fRank: &d" + data.rank(),
 *                 "&fBlocks: &e" + Numbers.commas(data.blocks()),
 *                 "",
 *                 "&7moonprison.net");
 *     }
 * }
 * }</pre>
 *
 * <p>Both methods are called on the main thread (the player's region on Folia) every update, so keep
 * them fast: read cached data, never query a database here.</p>
 */
public interface BoardProvider {

    /**
     * Returns the title for a player.
     *
     * @param player the player
     * @return the title, colour codes allowed
     */
    String title(Player player);

    /**
     * Returns the lines for a player, top to bottom. Return {@code null} to hide the board for this
     * player for now.
     *
     * @param player the player
     * @return up to 15 lines, colour codes allowed, or {@code null}
     */
    List<String> lines(Player player);
}
