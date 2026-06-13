package net.kgomc.zelda.outbox.poller;

import com.google.gson.Gson;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import net.kgomc.zelda.core.reactive.ZeldaSchedulers;
import net.kgomc.zelda.database.locking.LockManager;
import net.kgomc.zelda.database.locking.ZeldaLock;
import net.kgomc.zelda.database.query.QueryRunner;
import net.kgomc.zelda.outbox.event.OutboxEvent;

import java.sql.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Polls {@code zelda_outbox} on a fixed interval, emits events to subscribers,
 * handles exponential backoff retry, and dead-letters after max attempts.
 *
 * <p>Events flow as an {@link Observable} per event type — handlers subscribe
 * to the type they care about.</p>
 */
public final class OutboxPoller {

    private final QueryRunner runner;
    private final LockManager lockManager;
    private final Logger      logger;
    private final int         pollIntervalSeconds;
    private final int         batchSize;



    /** Emits every event polled — subscribers filter by type */
    private final PublishSubject<OutboxEvent> subject = PublishSubject.create();

    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r ->
                    Thread.ofVirtual().name("zelda-outbox-poller").unstarted(r));

    private ScheduledFuture<?> pollTask;

    private final String pollLock;
    private final String pollSql;
    private final String markProcessing;
    private final String markDone;
    private final String markRetry;
    private final String moveToDead;
    private final String deleteFromOutbox;

    public OutboxPoller(QueryRunner runner, LockManager lockManager, Logger logger,
                        int pollIntervalSeconds, int batchSize, String schema) {
        this.runner              = runner;
        this.lockManager         = lockManager;
        this.logger              = logger;
        this.pollIntervalSeconds = pollIntervalSeconds;
        this.batchSize           = batchSize;
        this.pollLock            = "zelda:outbox:poll:" + schema;

        this.pollSql = """
            SELECT id, event_type, payload, attempts, max_attempts, created_at, process_at
            FROM "%s".zelda_outbox
            WHERE status = 'PENDING' AND process_at <= ?
            ORDER BY process_at ASC
            LIMIT ?
            """.formatted(schema);

        this.markProcessing = """
            UPDATE "%s".zelda_outbox SET status = 'PROCESSING' WHERE id = ?
            """.formatted(schema);

        this.markDone = """
            UPDATE "%s".zelda_outbox SET status = 'DONE', processed_at = ? WHERE id = ?
            """.formatted(schema);

        this.markRetry = """
            UPDATE "%s".zelda_outbox
            SET status = 'PENDING', attempts = ?, process_at = ?
            WHERE id = ?
            """.formatted(schema);

        this.moveToDead = """
            INSERT INTO "%s".zelda_outbox_dead
                (id, event_type, payload, attempts, max_attempts, created_at, failed_at, last_error)
            SELECT id, event_type, payload, attempts, max_attempts, created_at, ?, ?
            FROM "%s".zelda_outbox WHERE id = ?
            """.formatted(schema, schema);

        this.deleteFromOutbox = """
            DELETE FROM "%s".zelda_outbox WHERE id = ?
            """.formatted(schema);
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void start() {
        pollTask = scheduler.scheduleAtFixedRate(
                this::poll,
                pollIntervalSeconds,
                pollIntervalSeconds,
                TimeUnit.SECONDS
        );
        logger.info("[Zelda/Outbox] Poller started — interval=" + pollIntervalSeconds + "s, batch=" + batchSize);
    }

    public void stop() {
        if (pollTask != null) pollTask.cancel(false);
        scheduler.shutdown();
        subject.onComplete();
        logger.info("[Zelda/Outbox] Poller stopped.");
    }

    // -----------------------------------------------------------------------
    // Observable
    // -----------------------------------------------------------------------

    /**
     * Returns an Observable that emits all events of the given type.
     * Runs on {@link ZeldaSchedulers#io()} by default.
     */
    public Observable<OutboxEvent> observe(String eventType) {
        return subject
                .filter(e -> e.getEventType().equals(eventType))
                .subscribeOn(ZeldaSchedulers.io());
    }

    /**
     * Returns an Observable that emits ALL events regardless of type.
     */
    public Observable<OutboxEvent> observeAll() {
        return subject.subscribeOn(ZeldaSchedulers.io());
    }

    // -----------------------------------------------------------------------
    // Polling
    // -----------------------------------------------------------------------

    private void poll() {
        // Non-blocking try — if another node is already polling, skip this cycle
        lockManager.tryAdvisory(pollLock).ifPresent(lock -> {
            try (lock) {
                List<OutboxEvent> events = fetchPending();
                if (events.isEmpty()) return;

                logger.fine("[Zelda/Outbox] Polled " + events.size() + " event(s).");

                for (OutboxEvent event : events) {
                    markProcessing(event.getId());
                    subject.onNext(event);
                }
            } catch (Exception e) {
                logger.log(Level.WARNING, "[Zelda/Outbox] Poll failed", e);
            }
        });
    }

    private List<OutboxEvent> fetchPending() {
        return runner.query(pollSql,
                rs -> new OutboxEvent(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("event_type"),
                        rs.getString("payload"),
                        rs.getInt("attempts"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("process_at").toInstant()
                ),
                Timestamp.from(Instant.now()),
                batchSize
        );
    }

    // -----------------------------------------------------------------------
    // Outcome reporting — called by OutboxModule after handler resolves
    // -----------------------------------------------------------------------

    public void markSuccess(UUID eventId) {
        try {
            runner.update(markDone, Timestamp.from(Instant.now()), eventId.toString());
        } catch (Exception e) {
            logger.log(Level.WARNING, "[Zelda/Outbox] Failed to mark event DONE: " + eventId, e);
        }
    }

    public void markFailure(OutboxEvent event, Throwable error, int maxAttempts) {
        int nextAttempts = event.getAttempts() + 1;

        if (nextAttempts >= maxAttempts) {
            deadLetter(event, error.getMessage());
        } else {
            // Exponential backoff: 1s, 2s, 4s, 8s...
            long backoffSeconds = (long) Math.pow(2, nextAttempts - 1);
            Instant nextProcess = Instant.now().plusSeconds(backoffSeconds);

            try {
                runner.update(markRetry,
                        nextAttempts,
                        Timestamp.from(nextProcess),
                        event.getId().toString()
                );
                logger.warning("[Zelda/Outbox] Event " + event.getId()
                        + " failed (attempt " + nextAttempts + "/" + maxAttempts
                        + "), retry in " + backoffSeconds + "s");
            } catch (Exception e) {
                logger.log(Level.SEVERE, "[Zelda/Outbox] Failed to schedule retry for: " + event.getId(), e);
            }
        }
    }

    private void deadLetter(OutboxEvent event, String lastError) {
        try {
            runner.transaction(conn -> {
                try (PreparedStatement insert = conn.prepareStatement(moveToDead)) {
                    insert.setTimestamp(1, Timestamp.from(Instant.now()));
                    insert.setString(2, lastError != null ? lastError.substring(0, Math.min(lastError.length(), 1000)) : null);
                    insert.setString(3, event.getId().toString());
                    insert.executeUpdate();
                }
                try (PreparedStatement delete = conn.prepareStatement(deleteFromOutbox)) {
                    delete.setString(1, event.getId().toString());
                    delete.executeUpdate();
                }
            });
            logger.severe("[Zelda/Outbox] Event dead-lettered: " + event.getId()
                    + " (type=" + event.getEventType() + ")");
        } catch (Exception e) {
            logger.log(Level.SEVERE, "[Zelda/Outbox] Failed to dead-letter event: " + event.getId(), e);
        }
    }

    private void markProcessing(UUID eventId) {
        runner.update(markProcessing, eventId.toString());
    }
}