package net.kgomc.zelda.database.query;

import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import net.kgomc.zelda.core.reactive.ZeldaSchedulers;
import net.kgomc.zelda.database.connection.ZeldaDataSource;
import net.kgomc.zelda.database.transaction.TransactionCallback;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Central query helper — wraps boilerplate JDBC, adds async support and transactions.
 *
 * <h2>Sync usage</h2>
 * <pre>{@code
 * List<Player> players = runner.query(
 *     "SELECT * FROM players WHERE coins > ?",
 *     rs -> new Player(rs.getString("uuid"), rs.getInt("coins")),
 *     100
 * );
 * }</pre>
 *
 * <h2>Async usage</h2>
 * <pre>{@code
 * runner.queryAsync("SELECT * FROM players", mapper)
 *       .thenAccept(list -> Bukkit.broadcastMessage("Players: " + list.size()));
 * }</pre>
 *
 * <h2>Transaction usage</h2>
 * <pre>{@code
 * runner.transaction(conn -> {
 *     runner.update(conn, "INSERT INTO log VALUES (?)", "login");
 *     runner.update(conn, "UPDATE players SET last_seen = NOW() WHERE uuid = ?", uuid);
 * });
 * }</pre>
 */
public final class QueryRunner {

    private final ZeldaDataSource dataSource;
    final Logger logger;   // package-accessible for DatabaseModule.migrations()
    private final Executor asyncExecutor;

    public QueryRunner(ZeldaDataSource dataSource, Logger logger) {
        this.dataSource    = dataSource;
        this.logger        = logger;
        // Virtual threads — Java 21, perfect for I/O-bound DB work
        this.asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    // -----------------------------------------------------------------------
    // Query — returns list
    // -----------------------------------------------------------------------

    /**
     * Executes a SELECT and maps every row via the supplied {@link RowMapper}.
     *
     * @param sql    parameterised SQL (use {@code ?} for bind parameters)
     * @param mapper row-to-object mapper
     * @param params bind parameters in order
     * @return list of mapped results (never null, may be empty)
     */
    public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = prepare(conn, sql, params);
             ResultSet rs = ps.executeQuery()) {

            List<T> results = new ArrayList<>();
            while (rs.next()) results.add(mapper.map(rs));
            return results;

        } catch (SQLException e) {
            throw new QueryException("query() failed: " + sql, e);
        }
    }

    /** Async variant of {@link #query}. Runs on a virtual thread. */
    public <T> CompletableFuture<List<T>> queryAsync(String sql, RowMapper<T> mapper, Object... params) {
        return CompletableFuture.supplyAsync(() -> query(sql, mapper, params), asyncExecutor);
    }

    // -----------------------------------------------------------------------
    // QueryOne — returns Optional
    // -----------------------------------------------------------------------

    /**
     * Executes a SELECT and returns the first row mapped, or {@link Optional#empty()}.
     */
    public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = prepare(conn, sql, params);
             ResultSet rs = ps.executeQuery()) {

            return rs.next() ? Optional.of(mapper.map(rs)) : Optional.empty();

        } catch (SQLException e) {
            throw new QueryException("queryOne() failed: " + sql, e);
        }
    }

    /** Async variant of {@link #queryOne}. */
    public <T> CompletableFuture<Optional<T>> queryOneAsync(String sql, RowMapper<T> mapper, Object... params) {
        return CompletableFuture.supplyAsync(() -> queryOne(sql, mapper, params), asyncExecutor);
    }

    // -----------------------------------------------------------------------
    // Update — INSERT / UPDATE / DELETE
    // -----------------------------------------------------------------------

    /**
     * Executes an INSERT, UPDATE, or DELETE and returns the affected row count.
     */
    public int update(String sql, Object... params) {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = prepare(conn, sql, params)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new QueryException("update() failed: " + sql, e);
        }
    }

    /**
     * Executes an UPDATE on an existing connection (for use inside transactions).
     */
    public int update(Connection conn, String sql, Object... params) {
        try (PreparedStatement ps = prepare(conn, sql, params)) {
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new QueryException("update(conn) failed: " + sql, e);
        }
    }

    /** Async variant of {@link #update(String, Object...)}. */
    public CompletableFuture<Integer> updateAsync(String sql, Object... params) {
        return CompletableFuture.supplyAsync(() -> update(sql, params), asyncExecutor);
    }

    // -----------------------------------------------------------------------
    // Execute — DDL (CREATE TABLE, etc.)
    // -----------------------------------------------------------------------

    /**
     * Executes a raw SQL statement (DDL). No parameters, no return value.
     */
    public void execute(String sql) {
        try (Connection conn = dataSource.getConnection();
             Statement st = conn.createStatement()) {
            st.execute(sql);
        } catch (SQLException e) {
            throw new QueryException("execute() failed: " + sql, e);
        }
    }

    // -----------------------------------------------------------------------
    // Transactions
    // -----------------------------------------------------------------------

    /**
     * Runs the given {@link TransactionCallback} inside a database transaction.
     *
     * <p>Commits automatically on success; rolls back on any exception and
     * re-throws a {@link QueryException}.</p>
     *
     * <pre>{@code
     * runner.transaction(conn -> {
     *     runner.update(conn, "INSERT INTO players (uuid) VALUES (?)", uuid);
     *     runner.update(conn, "INSERT INTO stats  (uuid) VALUES (?)", uuid);
     * });
     * }</pre>
     */
    public void transaction(TransactionCallback callback) {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try {
                callback.execute(conn);
                conn.commit();
            } catch (Exception e) {
                safeRollback(conn);
                throw new QueryException("Transaction rolled back", e);
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new QueryException("Failed to obtain connection for transaction", e);
        }
    }

    /** Async variant of {@link #transaction}. */
    public CompletableFuture<Void> transactionAsync(TransactionCallback callback) {
        return CompletableFuture.runAsync(() -> transaction(callback), asyncExecutor);
    }

    // -----------------------------------------------------------------------
    // Reactive — RxJava wrappers (subscribeOn ZeldaSchedulers.io() recommended)
    // -----------------------------------------------------------------------

    /**
     * Reactive {@link #query} — emits a list of mapped results.
     *
     * <pre>{@code
     * runner.queryRx("SELECT * FROM players", mapper)
     *     .subscribeOn(ZeldaSchedulers.io())
     *     .observeOn(ZeldaSchedulers.serverThread())
     *     .subscribe(list -> { ... }, error -> { ... });
     * }</pre>
     */
    public <T> Single<List<T>> queryRx(String sql, RowMapper<T> mapper, Object... params) {
        return Single.fromCallable(() -> query(sql, mapper, params))
                .subscribeOn(ZeldaSchedulers.io());
    }

    /**
     * Reactive {@link #queryOne} — emits the first matching row or completes empty.
     *
     * <pre>{@code
     * runner.queryOneRx("SELECT * FROM players WHERE uuid = ?", mapper, uuid)
     *     .subscribeOn(ZeldaSchedulers.io())
     *     .subscribe(player -> { ... }, error -> { ... }, () -> { /* not found *\/ });
     * }</pre>
     */
    public <T> Maybe<T> queryOneRx(String sql, RowMapper<T> mapper, Object... params) {
        return Maybe.fromCallable(() -> queryOne(sql, mapper, params).orElse(null))
                .subscribeOn(ZeldaSchedulers.io());
    }

    /**
     * Reactive {@link #update} — emits the affected row count.
     *
     * <pre>{@code
     * runner.updateRx("UPDATE players SET coins = ? WHERE uuid = ?", coins, uuid)
     *     .subscribeOn(ZeldaSchedulers.io())
     *     .subscribe(rows -> { ... });
     * }</pre>
     */
    public Single<Integer> updateRx(String sql, Object... params) {
        return Single.fromCallable(() -> update(sql, params))
                .subscribeOn(ZeldaSchedulers.io());
    }

    /**
     * Reactive {@link #transaction} — completes when the transaction commits,
     * errors if it rolls back.
     *
     * <pre>{@code
     * runner.transactionRx(conn -> {
     *         runner.update(conn, "INSERT INTO log VALUES (?)", "login");
     *         runner.update(conn, "UPDATE players SET last_seen = NOW() WHERE uuid = ?", uuid);
     *     })
     *     .subscribeOn(ZeldaSchedulers.io())
     *     .subscribe(() -> { /* committed *\/ }, error -> { /* rolled back *\/ });
     * }</pre>
     */
    public Completable transactionRx(TransactionCallback callback) {
        return Completable.fromAction(() -> transaction(callback))
                .subscribeOn(ZeldaSchedulers.io());
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private PreparedStatement prepare(Connection conn, String sql, Object... params) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            ps.setObject(i + 1, params[i]);
        }
        return ps;
    }

    private void safeRollback(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "[Zelda/DB] Rollback failed", e);
        }
    }
}