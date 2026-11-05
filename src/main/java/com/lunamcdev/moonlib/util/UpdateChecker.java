package com.lunamcdev.moonlib.util;

import com.lunamcdev.moonlib.MoonLib;
import com.lunamcdev.moonlib.config.Json;
import com.lunamcdev.moonlib.config.MalformedDataException;
import com.lunamcdev.moonlib.scheduler.TaskHandle;
import com.lunamcdev.moonlib.scheduler.Tasks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Checks whether a newer version of a plugin has been published, and tells the console and staff.
 *
 * <pre>{@code
 * UpdateChecker.modrinth(this, "moonprison")       // or spigot(this, 12345), github(this, "LunaMCDev/MoonPrison"),
 *         .notifyPermission("moonprison.admin")    //    hangar(this, "LunaMCDev/MoonPrison")
 *         .checkEvery(6, TimeUnit.HOURS)
 *         .start();
 * }</pre>
 *
 * <p>Checks run off the server thread and never throw: a network problem is logged at debug level
 * and the next check simply tries again. Versions are compared numerically
 * ({@code 1.10.0 > 1.9.2}), ignoring a leading {@code v}; a pre-release such as
 * {@code 2.0.0-beta} is older than {@code 2.0.0}.</p>
 *
 * @since 1.2.0
 */
public final class UpdateChecker implements AutoCloseable {

    /** Where releases are published. */
    public enum Source {
        /** Modrinth project (slug or id). */
        MODRINTH,
        /** SpigotMC resource id. */
        SPIGOT,
        /** GitHub repository ({@code owner/repo}), using the latest release's tag. */
        GITHUB,
        /** Hangar project ({@code owner/slug} or slug). */
        HANGAR
    }

    /** The outcome of a check. */
    public static final class Result {
        private final String current;
        private final String latest;
        private final String downloadUrl;

        Result(String current, String latest, String downloadUrl) {
            this.current = current;
            this.latest = latest;
            this.downloadUrl = downloadUrl;
        }

        /**
         * Returns the running version.
         *
         * @return the current version
         */
        public String current() {
            return current;
        }

        /**
         * Returns the newest published version.
         *
         * @return the latest version
         */
        public String latest() {
            return latest;
        }

        /**
         * Returns where to download the update.
         *
         * @return the URL
         */
        public String downloadUrl() {
            return downloadUrl;
        }

        /**
         * Returns whether the published version is newer than the running one.
         *
         * @return {@code true} if an update is available
         */
        public boolean isUpdateAvailable() {
            return compare(latest, current) > 0;
        }
    }

    private final Plugin plugin;
    private final Source source;
    private final String project;
    private String permission;
    private long intervalTicks;
    private String message = "&d{plugin} &7has an update: &f{current} &7-> &a{latest} &8({url})";
    private Consumer<Result> onUpdate;
    private volatile Result lastResult;
    private TaskHandle timer;
    private Listener joinListener;

    private UpdateChecker(Plugin plugin, Source source, String project) {
        this.plugin = plugin;
        this.source = source;
        this.project = project;
    }

    /**
     * Checks a Modrinth project.
     *
     * @param plugin  the plugin
     * @param project the project slug or id
     * @return a checker
     */
    public static UpdateChecker modrinth(Plugin plugin, String project) {
        return new UpdateChecker(plugin, Source.MODRINTH, project);
    }

    /**
     * Checks a SpigotMC resource.
     *
     * @param plugin     the plugin
     * @param resourceId the resource id from its URL
     * @return a checker
     */
    public static UpdateChecker spigot(Plugin plugin, int resourceId) {
        return new UpdateChecker(plugin, Source.SPIGOT, String.valueOf(resourceId));
    }

    /**
     * Checks the latest release of a GitHub repository.
     *
     * @param plugin     the plugin
     * @param repository {@code owner/repo}
     * @return a checker
     */
    public static UpdateChecker github(Plugin plugin, String repository) {
        return new UpdateChecker(plugin, Source.GITHUB, repository);
    }

    /**
     * Checks a Hangar project.
     *
     * @param plugin  the plugin
     * @param project {@code owner/slug} or the slug
     * @return a checker
     */
    public static UpdateChecker hangar(Plugin plugin, String project) {
        return new UpdateChecker(plugin, Source.HANGAR, project);
    }

    /**
     * Also tells online players with this permission, and players who join with it later.
     *
     * @param node the permission
     * @return this checker
     */
    public UpdateChecker notifyPermission(String node) {
        this.permission = node;
        return this;
    }

    /**
     * Repeats the check. By default it runs once, on {@link #start()}.
     *
     * @param interval the interval
     * @param unit     its unit
     * @return this checker
     */
    public UpdateChecker checkEvery(long interval, TimeUnit unit) {
        this.intervalTicks = Math.max(20L * 60, unit.toMillis(interval) / 50);
        return this;
    }

    /**
     * Sets the notification text. Placeholders: {@code {plugin}}, {@code {current}},
     * {@code {latest}}, {@code {url}}.
     *
     * @param text the message, colour codes allowed
     * @return this checker
     */
    public UpdateChecker message(String text) {
        this.message = text;
        return this;
    }

    /**
     * Runs custom code (on the server thread) when an update is found, in addition to the messages.
     *
     * @param action the action
     * @return this checker
     */
    public UpdateChecker onUpdate(Consumer<Result> action) {
        this.onUpdate = action;
        return this;
    }

    /**
     * Starts checking: once now (asynchronously), then on the interval if one is set.
     *
     * @return this checker
     */
    public UpdateChecker start() {
        MoonLib.runAs(plugin, () -> {
            if (intervalTicks > 0) {
                timer = Tasks.asyncTimer(20, intervalTicks, this::checkAndNotify);
            } else {
                Tasks.async(this::checkAndNotify);
            }
            if (permission != null) {
                joinListener = new Listener() {
                    @EventHandler(priority = EventPriority.MONITOR)
                    public void onJoin(PlayerJoinEvent event) {
                        Result result = lastResult;
                        Player player = event.getPlayer();
                        if (result != null && result.isUpdateAvailable() && player.hasPermission(permission)) {
                            Tasks.entityLater(player, 40, () -> player.sendMessage(format(result)));
                        }
                    }
                };
                Bukkit.getPluginManager().registerEvents(joinListener, plugin);
            }
            if (MoonLib.isInitialised(plugin)) {
                MoonLib.context(plugin).onShutdown(this);
            }
        });
        return this;
    }

    /**
     * Checks once, without notifying anyone.
     *
     * @return a future of the result; completes exceptionally if the check failed
     */
    public CompletableFuture<Result> check() {
        return CompletableFuture.supplyAsync(this::fetch);
    }

    /**
     * Returns the result of the last successful check.
     *
     * @return the result, or empty before the first check finishes
     */
    public Optional<Result> lastResult() {
        return Optional.ofNullable(lastResult);
    }

    /** Stops the timer and the join notifications. */
    @Override
    public void close() {
        if (timer != null) {
            timer.cancel();
        }
        if (joinListener != null) {
            HandlerList.unregisterAll(joinListener);
        }
    }

    // ------------------------------------------------------------------ internals

    private void checkAndNotify() {
        Result result;
        try {
            result = fetch();
        } catch (RuntimeException e) {
            plugin.getLogger().log(Level.FINE, "Update check failed", e);
            return;
        }
        Result previous = lastResult;
        lastResult = result;
        if (!result.isUpdateAvailable() || (previous != null && previous.latest.equals(result.latest))) {
            return;
        }
        plugin.getLogger().info(Text.strip(format(result)));
        MoonLib.runAs(plugin, () -> Tasks.sync(() -> {
            if (permission != null) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.hasPermission(permission)) {
                        player.sendMessage(format(result));
                    }
                }
            }
            if (onUpdate != null) {
                onUpdate.accept(result);
            }
        }));
    }

    private String format(Result result) {
        return Text.color(Text.replace(message, "plugin", plugin.getName(), "current", result.current,
                "latest", result.latest, "url", result.downloadUrl));
    }

    private Result fetch() {
        @SuppressWarnings("deprecation")
        String current = plugin.getDescription().getVersion();
        String body = get(apiUrl(source, project));
        return new Result(current, parseLatest(source, body), pageUrl(source, project));
    }

    static String apiUrl(Source source, String project) {
        switch (source) {
            case MODRINTH:
                return "https://api.modrinth.com/v2/project/" + project + "/version?include_changelog=false";
            case SPIGOT:
                return "https://api.spigotmc.org/legacy/update.php?resource=" + project;
            case GITHUB:
                return "https://api.github.com/repos/" + project + "/releases/latest";
            case HANGAR:
                return "https://hangar.papermc.io/api/v1/projects/" + project.substring(project.indexOf('/') + 1)
                        + "/latestrelease";
            default:
                throw new IllegalArgumentException(String.valueOf(source));
        }
    }

    static String pageUrl(Source source, String project) {
        switch (source) {
            case MODRINTH:
                return "https://modrinth.com/plugin/" + project;
            case SPIGOT:
                return "https://www.spigotmc.org/resources/" + project;
            case GITHUB:
                return "https://github.com/" + project + "/releases/latest";
            case HANGAR:
                return "https://hangar.papermc.io/" + project;
            default:
                return "";
        }
    }

    /**
     * Extracts the latest version from an API response.
     *
     * @param source the source
     * @param body   the response body
     * @return the version, without a leading {@code v}
     * @throws IllegalStateException if the response has no version
     */
    @SuppressWarnings("unchecked")
    static String parseLatest(Source source, String body) {
        String version;
        try {
            switch (source) {
                case MODRINTH: {
                    Object parsed = Json.parse(body);
                    if (!(parsed instanceof List) || ((List<?>) parsed).isEmpty()) {
                        throw new IllegalStateException("The Modrinth project has no versions");
                    }
                    String found = null;
                    for (Object entry : (List<Object>) parsed) {
                        Map<String, Object> v = (Map<String, Object>) entry;
                        if ("release".equals(v.get("version_type"))) {
                            found = String.valueOf(v.get("version_number"));
                            break;
                        }
                    }
                    version = found != null ? found
                            : String.valueOf(((Map<String, Object>) ((List<Object>) parsed).get(0)).get("version_number"));
                    break;
                }
                case GITHUB: {
                    Object tag = Json.parseObject(body).get("tag_name");
                    if (tag == null) {
                        throw new IllegalStateException("The GitHub repository has no releases");
                    }
                    version = tag.toString();
                    break;
                }
                default:
                    version = body.trim();
            }
        } catch (MalformedDataException | ClassCastException e) {
            throw new IllegalStateException("Unexpected update API response: " + e.getMessage(), e);
        }
        if (version.isEmpty() || version.length() > 64) {
            throw new IllegalStateException("Unexpected update API response");
        }
        return version.startsWith("v") || version.startsWith("V") ? version.substring(1) : version;
    }

    /**
     * Compares versions numerically, segment by segment. A version with a pre-release suffix
     * ({@code -beta}, {@code -rc.1}, {@code -SNAPSHOT}) is older than the same version without one.
     *
     * @param a one version
     * @param b another version
     * @return negative, zero or positive
     */
    public static int compare(String a, String b) {
        String[] pa = splitSuffix(a);
        String[] pb = splitSuffix(b);
        int numeric = MoonLib.compareVersions(pa[0], pb[0]);
        if (numeric != 0) {
            return numeric;
        }
        if (pa[1].isEmpty() != pb[1].isEmpty()) {
            return pa[1].isEmpty() ? 1 : -1;
        }
        return pa[1].compareToIgnoreCase(pb[1]);
    }

    private static String[] splitSuffix(String version) {
        String v = version.trim();
        if (v.startsWith("v") || v.startsWith("V")) {
            v = v.substring(1);
        }
        int dash = v.indexOf('-');
        int plus = v.indexOf('+');
        if (plus >= 0 && (dash < 0 || plus < dash)) {
            v = v.substring(0, plus);
            dash = v.indexOf('-');
        }
        return dash < 0 ? new String[]{v, ""} : new String[]{v.substring(0, dash), v.substring(dash + 1)};
    }

    private String get(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            @SuppressWarnings("deprecation")
            String version = plugin.getDescription().getVersion();
            connection.setRequestProperty("User-Agent", plugin.getName() + "/" + version + " (MoonLib update checker)");
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            if (code != 200) {
                throw new IllegalStateException("Update API answered HTTP " + code + " for " + url);
            }
            try (InputStream in = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1 && out.size() < 1_000_000) {
                    out.write(buffer, 0, read);
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not reach " + url + ": " + e.getMessage(), e);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }
}
