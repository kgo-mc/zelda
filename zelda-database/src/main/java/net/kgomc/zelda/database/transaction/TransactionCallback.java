package net.kgomc.zelda.database.transaction;

import net.kgomc.zelda.database.query.QueryRunner;

import java.sql.Connection;

/**
 * A unit of work executed inside a database transaction.
 *
 * <p>Throw any exception to trigger a rollback. The {@link QueryRunner}
 * will catch it, roll back, and re-throw as a {@link net.kgomc.zelda.database.query.QueryException}.</p>
 */
@FunctionalInterface
public interface TransactionCallback {
    void execute(Connection conn) throws Exception;
}