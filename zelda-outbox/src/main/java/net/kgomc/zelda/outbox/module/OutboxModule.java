package net.kgomc.zelda.outbox.module;

import com.google.gson.Gson;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Scheduler;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kgomc.zelda.core.context.ZeldaContext;
import net.kgomc.zelda.core.lifecycle.LifecycleHook;
import net.kgomc.zelda.core.module.ZeldaModule;
import net.kgomc.zelda.core.reactive.ZeldaSchedulers;
import net.kgomc.zelda.database.module.DatabaseModule;
import net.kgomc.zelda.database.query.QueryRunner;
import net.kgomc.zelda.outbox.event.OutboxEvent;
import net.kgomc.zelda.outbox.migration.OutboxMigration;
import net.kgomc.zelda.outbox.poller.OutboxPoller;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Zelda outbox module — transactional outbox pattern + RxJava event bus.
 *
 * <p>Implements {@link LifecycleHook} — migration runs and the poller starts
 * in {@link #afterAllEnabled()}, guaranteeing {@link DatabaseModule} is fully
 * up before we touch the DB.</p>
 *
 * <h2>Setup</h2>
 * <pre>{@code
 * Zelda.builder()
 *     .withDatabase()
 *     .withOutbox()
 *     .initialize(adapter);
 * // Migration runs automatically — no manual registration needed.
 * }</pre>
 *
 * <h2>Publish inside a transaction (atomically safe)</h2>
 * <pre>{@code
 * runner.transaction(conn -> {
 *     runner.update(conn, "UPDATE players SET coins = coins - ? WHERE uuid = ?", cost, uuid);
 *     outbox.publish(conn, "player.purchase", Map.of("uuid", uuid, "item", "sword", "cost", cost));
 * });
 * }</pre>
 *
 * <h2>Subscribe — synchronous handler</h2>
 * <pre>{@code
 * outbox.subscribe("player.login", event -> {
 *     stats.recordLogin(event.getUUID("uuid"));
 * });
 * }</pre>
 *
 * <h2>Subscribe — async, deliver on server thread</h2>
 * <pre>{@code
 * outbox.subscribeAsync("player.purchase",
 *     event -> receiptService.generate(event),
 *     event -> player.sendMessage("Purchase confirmed!")
 * );
 * }</pre>
 *
 * <h2>Raw Observable — full RxJava control</h2>
 * <pre>{@code
 * outbox.observe("player.purchase")
 *     .filter(e -> e.getInt("cost") > 1000)
 *     .subscribeOn(ZeldaSchedulers.io())
 *     .observeOn(ZeldaSchedulers.serverThread())
 *     .subscribe(event -> { ... });
 * }</pre>
 */
public final class OutboxModule implements ZeldaModule, LifecycleHook {

    private static final Gson GSON = new Gson();


    private final int pollIntervalSeconds;
    private final int batchSize;
    private final int defaultMaxAttempts;

    private String schema;
    private ZeldaContext context;
    private DatabaseModule dbModule;
    private QueryRunner    runner;
    private OutboxPoller   poller;
    private Logger         logger;

    /** Tracks all subscriptions so they can be disposed on shutdown */
    private final CompositeDisposable disposables = new CompositeDisposable();

    public OutboxModule(int pollIntervalSeconds, int batchSize, int defaultMaxAttempts, String schema) {
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.batchSize           = batchSize;
        this.defaultMaxAttempts  = defaultMaxAttempts;
        this.schema              = schema;
    }

    public OutboxModule(String schema) {
        this(5, 50, 3, schema);
    }

    // -----------------------------------------------------------------------
    // ZeldaModule
    // -----------------------------------------------------------------------

    @Override
    public String getName() { return "outbox"; }

    @Override
    public void onEnable(ZeldaContext context) {
        // Just store context — DB may not be fully ready yet.
        // Real setup happens in afterAllEnabled().
        this.context = context;
        this.logger  = context.getLogger();
        logger.info("[Zelda/Outbox] Waiting for all modules before running migrations...");
    }

    @Override
    public void onDisable() {
        disposables.dispose();
        if (poller != null) poller.stop();
    }

    // -----------------------------------------------------------------------
    // LifecycleHook — fires after ALL modules have completed onEnable()
    // -----------------------------------------------------------------------

    @Override
    public void afterAllEnabled() {
        // Now safe to get DatabaseModule — it's fully initialised
        dbModule = context.getRegistry()
                .find(DatabaseModule.class)
                .orElseThrow(() -> new IllegalStateException(
                        "[Zelda/Outbox] DatabaseModule is required. Add .withDatabase() to the builder."));

        runner = dbModule.getRunner();

        // Run outbox migration (locked — safe against concurrent nodes)
        logger.info("[Zelda/Outbox] Running outbox migration...");
        dbModule.migrations()
                .schema(schema)
                .register(new OutboxMigration(schema))
                .run();

        // Start poller — uses advisory lock per poll batch
        poller = new OutboxPoller(runner, dbModule.getLockManager(), logger,
                pollIntervalSeconds, batchSize, schema);
        poller.start();

        logger.info("[Zelda/Outbox] Ready — poll=" + pollIntervalSeconds
                + "s, batch=" + batchSize + ", maxAttempts=" + defaultMaxAttempts);
    }

    // -----------------------------------------------------------------------
    // Publish
    // -----------------------------------------------------------------------

    /**
     * Publishes an event inside an existing connection/transaction.
     * Persisted atomically with whatever else the transaction does.
     */
    public void publish(Connection conn, String eventType, Map<String, Object> payload) {
        publish(conn, eventType, payload, Instant.now(), defaultMaxAttempts);
    }

    public void publish(Connection conn, String eventType, Map<String, Object> payload,
                        Instant processAt, int maxAttempts) {
        try (PreparedStatement ps = conn.prepareStatement(insertSql())) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, eventType);
            ps.setString(3, GSON.toJson(payload));
            ps.setInt(4, maxAttempts);
            ps.setTimestamp(5, Timestamp.from(Instant.now()));
            ps.setTimestamp(6, Timestamp.from(processAt));
            ps.executeUpdate();
        } catch (Exception e) {
            throw new RuntimeException("[Zelda/Outbox] Failed to publish event: " + eventType, e);
        }
    }

    /** Publishes an event in its own standalone transaction. */
    public void publish(String eventType, Map<String, Object> payload) {
        runner.transaction(conn -> publish(conn, eventType, payload));
    }

    /** Publishes an event to be processed after the given delay. */
    public void publishDelayed(String eventType, Map<String, Object> payload, Duration delay) {
        runner.transaction(conn ->
                publish(conn, eventType, payload, Instant.now().plus(delay), defaultMaxAttempts));
    }

    // -----------------------------------------------------------------------
    // Subscribe
    // -----------------------------------------------------------------------

    /**
     * Subscribes a synchronous handler on the IO scheduler.
     * On success: marks DONE. On failure: exponential backoff retry.
     */
    public void subscribe(String eventType, Consumer<OutboxEvent> handler) {
        Disposable d = poller.observe(eventType)
                .subscribeOn(ZeldaSchedulers.io())
                .subscribe(
                        event -> {
                            try {
                                handler.accept(event);
                                poller.markSuccess(event.getId());
                            } catch (Exception e) {
                                logger.warning("[Zelda/Outbox] Handler failed for " + event + ": " + e.getMessage());
                                poller.markFailure(event, e, defaultMaxAttempts);
                            }
                        },
                        error -> logger.severe("[Zelda/Outbox] Fatal stream error: " + error.getMessage())
                );
        disposables.add(d);
    }

    /**
     * Subscribes an async handler — processes on IO thread, delivers result on server thread.
     *
     * @param asyncHandler runs on IO thread — blocking work goes here
     * @param onComplete   runs on server main thread after success
     */
    public void subscribeAsync(String eventType,
                               Consumer<OutboxEvent> asyncHandler,
                               Consumer<OutboxEvent> onComplete) {
        Disposable d = poller.observe(eventType)
                .subscribeOn(ZeldaSchedulers.io())
                .flatMap(event -> Observable.fromCallable(() -> {
                    asyncHandler.accept(event);
                    return event;
                }).subscribeOn(ZeldaSchedulers.io()))
                .observeOn(ZeldaSchedulers.serverThread())
                .subscribe(
                        event -> {
                            poller.markSuccess(event.getId());
                            if (onComplete != null) onComplete.accept(event);
                        },
                        error -> logger.severe("[Zelda/Outbox] Async handler error: " + error.getMessage())
                );
        disposables.add(d);
    }

    /**
     * Subscribes an asynchronous handler to process events on an IO thread and delivers the result on a specified scheduler.
     *
     * The provided `asyncHandler` is executed on the IO thread for handling events asynchronously.
     * The `onComplete` consumer is invoked on the specified `observeOn` scheduler after the successful
     * processing of each event.
     *
     * @param eventType   the type of the events to subscribe to
     * @param asyncHandler a consumer that processes the events on an IO thread; blocking operations can be performed here
     * @param onComplete   a consumer that is called on the specified scheduler after successful processing of each event
     * @param observeOn    the scheduler on which the results or `onComplete` consumer will be invoked
     */
    public void subscribeOnAsync(String eventType,
                                 Consumer<OutboxEvent> asyncHandler,
                                 Consumer<OutboxEvent> onComplete,
                                 Scheduler observeOn) {
        Disposable d = poller.observe(eventType)
                .subscribeOn(ZeldaSchedulers.io())
                .flatMap(event -> Observable.fromCallable(() -> {
                    asyncHandler.accept(event);
                    return event;
                }).subscribeOn(ZeldaSchedulers.io()))
                .observeOn(observeOn)
                .subscribe(
                        event -> {
                            poller.markSuccess(event.getId());
                            if (onComplete != null) onComplete.accept(event);
                        },
                        error -> logger.severe("[Zelda/Outbox] Async handler error: " + error.getMessage())
                );
        disposables.add(d);
    }


    /**
     * Subscribes an asynchronous handler to process events on an IO thread and delivers the result on specified schedulers.
     *
     * The provided `asyncHandler` is executed on the specified `subscribeOn` scheduler for processing events asynchronously.
     * The `onComplete` consumer is invoked on the specified `observeOn` scheduler after successful processing of each event.
     *
     * @param eventType     the type of the events to subscribe to
     * @param asyncHandler  a consumer that processes the events asynchronously; blocking operations can be performed here
     * @param onComplete    a consumer that is called on the specified scheduler after successful processing of each event
     * @param observeOn     the scheduler on which the results or `onComplete` consumer will be invoked
     * @param subscribeOn   the scheduler on which the `asyncHandler` will be executed
     */
    public void subscribeOnAsync(String eventType,
                                 Consumer<OutboxEvent> asyncHandler,
                                 Consumer<OutboxEvent> onComplete,
                                 Scheduler observeOn,
                                 Scheduler subscribeOn) {
        Disposable d = poller.observe(eventType)
                .subscribeOn(subscribeOn)
                .flatMap(event -> Observable.fromCallable(() -> {
                    asyncHandler.accept(event);
                    return event;
                }).subscribeOn(subscribeOn))
                .observeOn(observeOn)
                .subscribe(
                        event -> {
                            poller.markSuccess(event.getId());
                            if (onComplete != null) onComplete.accept(event);
                        },
                        error -> logger.severe("[Zelda/Outbox] Async handler error: " + error.getMessage())
                );
        disposables.add(d);
    }


    /**
     * Returns the raw {@link Observable} for full RxJava pipeline control.
     * You are responsible for calling {@link OutboxPoller#markSuccess} or
     * {@link OutboxPoller#markFailure} on the event.
     */
    public Observable<OutboxEvent> observe(String eventType) {
        return poller.observe(eventType);
    }

    /** Returns the raw poller for advanced use cases. */
    public OutboxPoller getPoller() { return poller; }

    private String insertSql() {
        return """
        INSERT INTO "%s".zelda_outbox
            (id, event_type, payload, status, attempts, max_attempts, created_at, process_at)
        VALUES (?, ?, ?, 'PENDING', 0, ?, ?, ?)
        """.formatted(schema);
    }
}