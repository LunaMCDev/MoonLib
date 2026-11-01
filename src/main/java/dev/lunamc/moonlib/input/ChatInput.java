package dev.lunamc.moonlib.input;

import dev.lunamc.moonlib.MoonLib;
import dev.lunamc.moonlib.scheduler.TaskHandle;
import dev.lunamc.moonlib.scheduler.Tasks;
import dev.lunamc.moonlib.util.Log;
import dev.lunamc.moonlib.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Asks a player to type something in chat, then hands the answer back on the main thread. The typed
 * message is hidden from other players.
 *
 * <pre>{@code
 * ChatInput.ask(player)
 *         .prompt("&eType a name for your mine, or &ccancel&e.")
 *         .validate(name -> name.matches("[A-Za-z0-9_]{3,16}"), "&cUse 3-16 letters, numbers or _.")
 *         .timeout(30)
 *         .onInput(name -> {
 *             mines.rename(player, name);
 *             new MineMenu(player).open();
 *         })
 *         .onCancel(() -> new MineMenu(player).open())
 *         .start();
 * }</pre>
 *
 * <p>Each prompt belongs to the plugin that started it: its callbacks run as that plugin and it is
 * dropped when that plugin disables, even when several plugins share MoonLib.</p>
 *
 * <p>Typical use is from a menu button: close the menu, ask, and re-open the menu with the result.
 * Starting a new prompt cancels any prompt the player already had open.</p>
 */
public final class ChatInput {

    private static final Map<UUID, Request> PENDING = new ConcurrentHashMap<>();
    private static final ChatListener LISTENER = new ChatListener();

    private ChatInput() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Makes sure the shared chat listener is registered. Called by {@link MoonLib#init(Plugin)}.
     *
     * @param plugin the plugin being initialised
     */
    public static void init(Plugin plugin) {
        MoonLib.shareListener(LISTENER);
    }

    /**
     * Drops every pending prompt started by a plugin, without running callbacks. Prompts of other
     * plugins sharing MoonLib are unaffected. Called by {@link MoonLib#shutdown(Plugin)}.
     *
     * @param plugin the plugin
     */
    public static void shutdown(Plugin plugin) {
        for (Map.Entry<UUID, Request> entry : PENDING.entrySet()) {
            Request request = entry.getValue();
            if (request.owner == plugin && PENDING.remove(entry.getKey(), request)) {
                request.timeoutTask.cancel();
            }
        }
    }

    /**
     * Starts building a prompt for a player.
     *
     * @param player the player
     * @return a builder; call {@link Request#start()} to begin
     */
    public static Request ask(Player player) {
        return new Request(player);
    }

    /**
     * Returns whether a player currently has a pending prompt.
     *
     * @param player the player
     * @return {@code true} if waiting for their input
     */
    public static boolean isWaiting(Player player) {
        return PENDING.containsKey(player.getUniqueId());
    }

    /**
     * Cancels a player's pending prompt, running its cancel callback.
     *
     * @param player the player
     */
    public static void cancel(Player player) {
        Request request = PENDING.remove(player.getUniqueId());
        if (request != null) {
            MoonLib.runAs(request.owner, request::finishCancelled);
        }
    }

    /**
     * A chat prompt under construction or in progress.
     */
    public static final class Request {
        private final Player player;
        private final Plugin owner;
        private String prompt;
        private long timeoutSeconds = 60;
        private String cancelWord = "cancel";
        private String timeoutMessage = "&cYou took too long to answer.";
        private String cancelledMessage = "&7Cancelled.";
        private Predicate<String> validator = input -> true;
        private String invalidMessage = "&cInvalid input, try again.";
        private Consumer<String> onInput = input -> { };
        private Runnable onCancel = () -> { };
        private TaskHandle timeoutTask = TaskHandle.NONE;

        private Request(Player player) {
            this.player = player;
            this.owner = MoonLib.plugin();
        }

        /**
         * Sets the message sent when the prompt starts.
         *
         * @param message the message, colour codes allowed
         * @return this request
         */
        public Request prompt(String message) {
            this.prompt = message;
            return this;
        }

        /**
         * Sets how long to wait before giving up. Defaults to 60 seconds; 0 waits forever.
         *
         * @param seconds the timeout
         * @return this request
         */
        public Request timeout(long seconds) {
            this.timeoutSeconds = Math.max(0, seconds);
            return this;
        }

        /**
         * Sets the word that cancels the prompt. Defaults to {@code cancel}; {@code null} disables it.
         *
         * @param word the word, case-insensitive
         * @return this request
         */
        public Request cancelWord(String word) {
            this.cancelWord = word;
            return this;
        }

        /**
         * Sets the message sent on timeout, or {@code null} for none.
         *
         * @param message the message
         * @return this request
         */
        public Request timeoutMessage(String message) {
            this.timeoutMessage = message;
            return this;
        }

        /**
         * Sets the message sent when the player cancels, or {@code null} for none.
         *
         * @param message the message
         * @return this request
         */
        public Request cancelledMessage(String message) {
            this.cancelledMessage = message;
            return this;
        }

        /**
         * Rejects input that fails a check; the player is asked again.
         *
         * @param check          returns {@code true} for valid input
         * @param invalidMessage the message sent for invalid input
         * @return this request
         */
        public Request validate(Predicate<String> check, String invalidMessage) {
            this.validator = check;
            this.invalidMessage = invalidMessage;
            return this;
        }

        /**
         * Sets what happens with valid input. Runs on the player's thread.
         *
         * @param handler the handler
         * @return this request
         */
        public Request onInput(Consumer<String> handler) {
            this.onInput = handler;
            return this;
        }

        /**
         * Sets what happens if the player cancels, times out or leaves. Runs on the player's thread
         * (not run when they leave the server).
         *
         * @param handler the handler
         * @return this request
         */
        public Request onCancel(Runnable handler) {
            this.onCancel = handler;
            return this;
        }

        /** Starts waiting for input, replacing any prompt the player already had. */
        public void start() {
            if (!MoonLib.isShared(LISTENER)) {
                MoonLib.shareListener(LISTENER);
            }
            MoonLib.runAs(owner, this::begin);
        }

        private void begin() {
            Request previous = PENDING.put(player.getUniqueId(), this);
            if (previous != null) {
                previous.timeoutTask.cancel();
            }
            if (prompt != null && !prompt.isEmpty()) {
                player.sendMessage(Text.color(prompt));
            }
            if (timeoutSeconds > 0) {
                timeoutTask = Tasks.entityLater(player, timeoutSeconds * 20L, () -> {
                    if (PENDING.remove(player.getUniqueId(), this)) {
                        send(timeoutMessage);
                        safeRun(onCancel);
                    }
                });
            }
        }

        private void handle(String message) {
            String input = message.trim();
            if (cancelWord != null && input.equalsIgnoreCase(cancelWord)) {
                if (PENDING.remove(player.getUniqueId(), this)) {
                    finishCancelled();
                }
                return;
            }
            if (!validator.test(input)) {
                send(invalidMessage);
                return;
            }
            if (PENDING.remove(player.getUniqueId(), this)) {
                timeoutTask.cancel();
                safeRun(() -> onInput.accept(input));
            }
        }

        private void finishCancelled() {
            timeoutTask.cancel();
            send(cancelledMessage);
            safeRun(onCancel);
        }

        private void send(String message) {
            if (message != null && !message.isEmpty() && player.isOnline()) {
                player.sendMessage(Text.color(message));
            }
        }

        private static void safeRun(Runnable action) {
            try {
                action.run();
            } catch (RuntimeException e) {
                Log.error("Error in chat input handler", e);
            }
        }
    }

    /** Internal listener. Public only because Bukkit requires listener methods to be accessible. */
    public static final class ChatListener implements Listener {

        private ChatListener() {
        }

        /**
         * Captures chat from players with a pending prompt.
         *
         * @param event the event
         */
        @EventHandler(priority = EventPriority.LOWEST)
        public void onChat(AsyncPlayerChatEvent event) {
            Request request = PENDING.get(event.getPlayer().getUniqueId());
            if (request == null) {
                return;
            }
            event.setCancelled(true);
            String message = event.getMessage();
            MoonLib.runAs(request.owner, () -> Tasks.entity(event.getPlayer(), () -> request.handle(message)));
        }

        /**
         * Drops prompts of players who leave.
         *
         * @param event the event
         */
        @EventHandler(priority = EventPriority.MONITOR)
        public void onQuit(PlayerQuitEvent event) {
            Request request = PENDING.remove(event.getPlayer().getUniqueId());
            if (request != null) {
                request.timeoutTask.cancel();
            }
        }
    }
}
