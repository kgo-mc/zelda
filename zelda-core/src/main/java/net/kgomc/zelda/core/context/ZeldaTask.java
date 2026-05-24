package net.kgomc.zelda.core.context;

/**
 * A handle to a scheduled repeating task returned by
 * {@link ZeldaPlugin#runTaskTimer(Runnable, long, long)}.
 *
 * <p>Call {@link #cancel()} to stop the task.</p>
 */
public interface ZeldaTask {

    /** Cancels this task. Safe to call multiple times. */
    void cancel();

    /** Returns true if this task has been cancelled. */
    boolean isCancelled();
}