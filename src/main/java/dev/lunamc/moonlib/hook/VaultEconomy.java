package dev.lunamc.moonlib.hook;

import dev.lunamc.moonlib.util.Numbers;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.lang.reflect.Method;

/**
 * Soft integration with a Vault economy, without a compile-time dependency on Vault.
 *
 * <pre>{@code
 * if (!VaultEconomy.isAvailable()) {
 *     Log.warn("No economy plugin found, shop disabled");
 * }
 *
 * if (VaultEconomy.withdraw(player, price)) {
 *     giveItem(player);
 * } else {
 *     player.sendMessage("You need " + VaultEconomy.format(price));
 * }
 * }</pre>
 *
 * <p>The provider is looked up lazily, so it does not matter whether the economy plugin enables before
 * or after yours. Add {@code softdepend: [Vault]} (or {@code depend}) to your {@code plugin.yml}.</p>
 */
public final class VaultEconomy {

    private static volatile Object provider;
    private static volatile Methods methods;

    private VaultEconomy() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns whether Vault and an economy provider are present.
     *
     * @return {@code true} if economy calls will work
     */
    public static boolean isAvailable() {
        return resolve() != null;
    }

    /**
     * Returns a player's balance.
     *
     * @param player the player
     * @return the balance, or {@code 0} if no economy is available
     */
    public static double balance(OfflinePlayer player) {
        Object economy = resolve();
        if (economy == null) {
            return 0;
        }
        return ((Number) call(methods.getBalance, economy, player)).doubleValue();
    }

    /**
     * Returns whether a player has at least an amount.
     *
     * @param player the player
     * @param amount the amount
     * @return {@code true} if they can afford it
     */
    public static boolean has(OfflinePlayer player, double amount) {
        Object economy = resolve();
        return economy != null && (Boolean) call(methods.has, economy, player, amount);
    }

    /**
     * Takes money from a player if they can afford it.
     *
     * @param player the player
     * @param amount the amount, must not be negative
     * @return {@code true} if the money was taken
     */
    public static boolean withdraw(OfflinePlayer player, double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        Object economy = resolve();
        if (economy == null || !has(player, amount)) {
            return false;
        }
        return success(call(methods.withdraw, economy, player, amount));
    }

    /**
     * Gives money to a player.
     *
     * @param player the player
     * @param amount the amount, must not be negative
     * @return {@code true} if the money was given
     */
    public static boolean deposit(OfflinePlayer player, double amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must not be negative");
        }
        Object economy = resolve();
        return economy != null && success(call(methods.deposit, economy, player, amount));
    }

    /**
     * Formats an amount using the economy plugin's currency format.
     *
     * @param amount the amount
     * @return the formatted amount, or a plain {@code $1,234.56} style fallback
     */
    public static String format(double amount) {
        Object economy = resolve();
        if (economy == null) {
            return "$" + Numbers.commas(amount);
        }
        return (String) call(methods.format, economy, amount);
    }

    /**
     * Returns the raw Vault {@code Economy} instance for anything not covered here.
     *
     * @return the provider, or {@code null}
     */
    public static Object rawProvider() {
        return resolve();
    }

    private static Object resolve() {
        Object current = provider;
        if (current != null) {
            return current;
        }
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(economyClass);
            if (registration == null || registration.getProvider() == null) {
                return null;
            }
            Class<?> response = Class.forName("net.milkbowl.vault.economy.EconomyResponse");
            Methods found = new Methods();
            found.getBalance = economyClass.getMethod("getBalance", OfflinePlayer.class);
            found.has = economyClass.getMethod("has", OfflinePlayer.class, double.class);
            found.withdraw = economyClass.getMethod("withdrawPlayer", OfflinePlayer.class, double.class);
            found.deposit = economyClass.getMethod("depositPlayer", OfflinePlayer.class, double.class);
            found.format = economyClass.getMethod("format", double.class);
            found.transactionSuccess = response.getMethod("transactionSuccess");
            methods = found;
            provider = registration.getProvider();
            return provider;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    private static Object call(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Vault call " + method.getName() + " failed", e);
        }
    }

    private static boolean success(Object response) {
        return response != null && (Boolean) call(methods.transactionSuccess, response);
    }

    private static final class Methods {
        Method getBalance;
        Method has;
        Method withdraw;
        Method deposit;
        Method format;
        Method transactionSuccess;
    }
}
