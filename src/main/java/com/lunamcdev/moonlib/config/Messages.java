package com.lunamcdev.moonlib.config;

import com.lunamcdev.moonlib.hook.Placeholders;
import com.lunamcdev.moonlib.util.Log;
import com.lunamcdev.moonlib.util.Text;
import com.lunamcdev.moonlib.util.Titles;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Configurable, translatable messages backed by a YAML file (default {@code messages.yml}).
 *
 * <pre>{@code
 * # messages.yml
 * prefix: "&d&lMoonPrison &8» "
 * rankup:
 *   success: "{prefix}&aYou ranked up to &e{rank}&a!"
 *   not-enough: "{prefix}&cYou need &e{cost} &cto rank up."
 * help:
 *   - "&d/rankup &7- Rank up"
 *   - "&d/mine &7- Teleport to your mine"
 * }</pre>
 *
 * <pre>{@code
 * Messages messages = new Messages(plugin);
 * messages.send(player, "rankup.success", "rank", "B");
 * messages.send(player, "help");                       // lists send one line each
 * String raw = messages.get("rankup.not-enough", "cost", "$5,000");
 * messages.broadcast("events.started", "event", "Double XP");
 * }</pre>
 *
 * <ul>
 *     <li>{@code {prefix}} is replaced with the {@code prefix} key in every message.</li>
 *     <li>Other placeholders are given as alternating key/value pairs.</li>
 *     <li>A message set to {@code ""} is not sent at all, so server owners can disable messages.</li>
 *     <li>A missing key shows {@code "Missing message: key"} and logs a warning once, instead of crashing.</li>
 *     <li>PlaceholderAPI placeholders are applied when sending to players, if enabled.</li>
 * </ul>
 */
public class Messages {

    private final ConfigFile file;
    private final Set<String> warnedMissing = ConcurrentHashMap.newKeySet();
    private boolean placeholderApi = true;
    private boolean richText;

    /**
     * Loads {@code messages.yml}.
     *
     * @param plugin the owning plugin
     */
    public Messages(Plugin plugin) {
        this(plugin, "messages.yml");
    }

    /**
     * Loads a custom messages file, e.g. {@code "lang/en.yml"}.
     *
     * @param plugin   the owning plugin
     * @param fileName the file name in the data folder
     */
    public Messages(Plugin plugin, String fileName) {
        this.file = new ConfigFile(plugin, fileName);
    }

    /**
     * Enables or disables PlaceholderAPI processing for player recipients. Enabled by default.
     *
     * @param enabled {@code true} to apply PlaceholderAPI
     * @return this instance
     */
    public Messages usePlaceholderApi(boolean enabled) {
        this.placeholderApi = enabled;
        return this;
    }

    /**
     * Formats messages with {@link com.lunamcdev.moonlib.util.RichText} tags such as
     * {@code <gradient:#7F7FD5:#91EAE4>} and {@code <bold>}, in addition to {@code &} codes.
     * Placeholder values are escaped, so a player name or chat message can never inject tags.
     * Off by default. Setting {@code rich-text: true} at the top of the file also turns it on.
     *
     * @param enabled {@code true} to format rich-text tags
     * @return this instance
     * @since 1.2.0
     */
    public Messages useRichText(boolean enabled) {
        this.richText = enabled;
        return this;
    }

    /** Reloads the file from disk and forgets which missing keys have been reported. */
    public void reload() {
        file.reload();
        warnedMissing.clear();
    }

    /**
     * Returns a coloured message with placeholders replaced. Lists are joined with new lines.
     *
     * @param key          the path in the file
     * @param placeholders alternating keys and values
     * @return the message
     */
    public String get(String key, Object... placeholders) {
        return String.join("\n", getList(key, placeholders));
    }

    /**
     * Returns a message as lines. A single string becomes a one-line list.
     *
     * @param key          the path in the file
     * @param placeholders alternating keys and values
     * @return the coloured lines; empty if the message is disabled with {@code ""}
     */
    public List<String> getList(String key, Object... placeholders) {
        List<String> raw = raw(key);
        List<String> out = new ArrayList<>(raw.size());
        String prefix = file.getString("prefix", "");
        boolean rich = richText || file.getBoolean("rich-text", false);
        Object[] values = rich ? escapeValues(placeholders) : placeholders;
        for (String line : raw) {
            String filled = Text.replace(line.replace("{prefix}", prefix), values);
            out.add(rich ? com.lunamcdev.moonlib.util.RichText.parse(filled) : Text.color(filled));
        }
        return out;
    }

    private static Object[] escapeValues(Object[] pairs) {
        if (pairs == null) {
            return null;
        }
        Object[] copy = pairs.clone();
        for (int i = 1; i < copy.length; i += 2) {
            copy[i] = com.lunamcdev.moonlib.util.RichText.escape(String.valueOf(copy[i]));
        }
        return copy;
    }

    /**
     * Sends a message. Does nothing if the message is empty.
     *
     * @param recipient    who to send to
     * @param key          the path in the file
     * @param placeholders alternating keys and values
     */
    public void send(CommandSender recipient, String key, Object... placeholders) {
        for (String line : getList(key, placeholders)) {
            if (placeholderApi && recipient instanceof Player) {
                line = Text.color(Placeholders.apply((Player) recipient, line));
            }
            recipient.sendMessage(line);
        }
    }

    /**
     * Sends a message to every online player and the console.
     *
     * @param key          the path in the file
     * @param placeholders alternating keys and values
     */
    public void broadcast(String key, Object... placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            send(player, key, placeholders);
        }
        send(Bukkit.getConsoleSender(), key, placeholders);
    }

    /**
     * Sends a message to every online player with a permission.
     *
     * @param permission   the permission
     * @param key          the path in the file
     * @param placeholders alternating keys and values
     */
    public void broadcastWithPermission(String permission, String key, Object... placeholders) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.hasPermission(permission)) {
                send(player, key, placeholders);
            }
        }
    }

    /**
     * Shows a message in the player's action bar.
     *
     * @param player       the player
     * @param key          the path in the file
     * @param placeholders alternating keys and values
     */
    public void actionBar(Player player, String key, Object... placeholders) {
        String message = get(key, placeholders);
        if (!message.isEmpty()) {
            Titles.actionBar(player, placeholderApi ? Placeholders.apply(player, message) : message);
        }
    }

    /**
     * Shows a title from two keys.
     *
     * @param player       the player
     * @param titleKey     the title's path, may point to {@code ""}
     * @param subtitleKey  the subtitle's path, may point to {@code ""}
     * @param placeholders alternating keys and values
     */
    public void title(Player player, String titleKey, String subtitleKey, Object... placeholders) {
        Titles.send(player, get(titleKey, placeholders), get(subtitleKey, placeholders));
    }

    /**
     * Returns the backing file, e.g. to read non-message settings stored alongside.
     *
     * @return the file
     */
    public ConfigFile file() {
        return file;
    }

    private List<String> raw(String key) {
        if (file.config().isList(key)) {
            return file.getStringList(key);
        }
        String value = file.getString(key);
        if (value == null) {
            if (warnedMissing.add(key)) {
                Log.warn("Missing message '{}' in {}", key, file.file().getName());
            }
            return Collections.singletonList("&cMissing message: " + key);
        }
        if (value.isEmpty()) {
            return Collections.emptyList();
        }
        return Collections.singletonList(value);
    }
}
