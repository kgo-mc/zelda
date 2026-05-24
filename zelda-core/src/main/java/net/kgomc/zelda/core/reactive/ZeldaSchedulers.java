package net.kgomc.zelda.core.reactive;

import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.schedulers.Schedulers;
import net.kgomc.zelda.core.context.ZeldaContext;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Central RxJava schedulers for the Zelda library.
 *
 * <p>Always use these rather than raw RxJava schedulers — they integrate
 * with the platform abstraction and ensure server-thread operations are
 * dispatched correctly on both Paper and Velocity.</p>
 *
 * <h2>Choosing a scheduler</h2>
 * <ul>
 *   <li>{@link #io()} — blocking I/O: database queries, file reads, network calls</li>
 *   <li>{@link #computation()} — CPU-bound work: parsing, sorting, calculations</li>
 *   <li>{@link #serverThread()} — anything that touches the Bukkit/Paper API (Paper only)</li>
 *   <li>{@link #newVirtualThread()} — ad-hoc virtual thread per subscription</li>
 * </ul>
 *
 * <h2>Typical pattern</h2>
 * <pre>{@code
 * db.getRunner()
 *     .queryRx("SELECT * FROM players WHERE uuid = ?", ResultSerializer.toObject(PlayerData.class), uuid)
 *     .subscribeOn(ZeldaSchedulers.io())           // query runs on IO thread
 *     .observeOn(ZeldaSchedulers.serverThread())   // result delivered on main thread
 *     .subscribe(data -> player.sendMessage("Coins: " + data.coins()));
 * }</pre>
 */
public final class ZeldaSchedulers {

    /** Virtual-thread-per-task executor — optimal for I/O-bound DB work. */
    private static final Executor VIRTUAL_EXECUTOR =
            Executors.newVirtualThreadPerTaskExecutor();

    /** Cached IO scheduler backed by virtual threads. */
    private static final Scheduler IO_SCHEDULER =
            Schedulers.from(VIRTUAL_EXECUTOR, true, true);

    private ZeldaSchedulers() {}

    // -----------------------------------------------------------------------
    // Schedulers
    // -----------------------------------------------------------------------

    /**
     * I/O scheduler — backed by virtual threads (Java 21).
     * Use for all blocking operations: DB queries, file reads, HTTP calls.
     */
    public static Scheduler io() {
        return IO_SCHEDULER;
    }

    /**
     * Computation scheduler — fixed thread pool sized to available processors.
     * Use for CPU-bound work that does not block.
     */
    public static Scheduler computation() {
        return Schedulers.computation();
    }

    /**
     * Server main-thread scheduler — dispatches work via {@link net.kgomc.zelda.core.context.ZeldaPlugin#runSync}.
     *
     * <p>Use when the result must be delivered on the server main thread
     * to safely call Bukkit/Paper APIs.</p>
     *
     * <p>On Velocity, this simply runs on the current thread (Velocity has no main thread).</p>
     *
     * @throws IllegalStateException if ZeldaContext has not been initialised
     */
    public static Scheduler serverThread() {
        return Schedulers.from(
                runnable -> ZeldaContext.get().getPlugin().runSync(runnable),
                true,
                false
        );
    }

    /**
     * Creates a new virtual-thread scheduler per subscription.
     * Useful for one-off tasks that should not share the IO pool.
     */
    public static Scheduler newVirtualThread() {
        return Schedulers.from(
                runnable -> Thread.ofVirtual().start(runnable),
                true,
                false
        );
    }
}