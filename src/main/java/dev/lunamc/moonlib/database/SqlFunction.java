package dev.lunamc.moonlib.database;

/**
 * A function that may throw any exception, used for transactions and asynchronous work.
 *
 * @param <A> the input type
 * @param <R> the result type
 * @since 1.1.1-alpha
 */
@FunctionalInterface
public interface SqlFunction<A, R> {

    /**
     * Applies the function.
     *
     * @param input the input
     * @return the result
     * @throws Exception anything; it is wrapped in a {@link DatabaseException} and, inside a
     *                   transaction, rolls the transaction back
     */
    R apply(A input) throws Exception;
}
