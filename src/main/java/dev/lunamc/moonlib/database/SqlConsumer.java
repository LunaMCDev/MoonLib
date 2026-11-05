package dev.lunamc.moonlib.database;

/**
 * An action that may throw any exception, used for transactions that return nothing.
 *
 * @param <A> the input type
 * @since 1.1.1-alpha
 */
@FunctionalInterface
public interface SqlConsumer<A> {

    /**
     * Runs the action.
     *
     * @param input the input
     * @throws Exception anything; inside a transaction it rolls the transaction back
     */
    void accept(A input) throws Exception;
}
