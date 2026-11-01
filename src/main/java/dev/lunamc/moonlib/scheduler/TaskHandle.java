package dev.lunamc.moonlib.scheduler;

/**
 * A handle to a scheduled task, independent of whether it runs on the Bukkit scheduler or one of
 * Folia's region schedulers.
 *
 * <pre>{@code
 * TaskHandle countdown = Tasks.timer(0, 20, () -> { ... });
 * // later
 * countdown.cancel();
 * }</pre>
 */
public interface TaskHandle {

    /** A handle that refers to nothing. Cancelling it does nothing. */
    TaskHandle NONE = new TaskHandle() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return true;
        }
    };

    /** Cancels the task. Safe to call more than once and from any thread. */
    void cancel();

    /**
     * Returns whether {@link #cancel()} has been called.
     *
     * @return {@code true} once cancelled
     */
    boolean isCancelled();
}
