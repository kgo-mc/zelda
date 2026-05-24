package net.kgomc.zelda.database.query;

/**
 * Unchecked exception thrown when a {@link QueryRunner} operation fails.
 * Wraps the underlying {@link java.sql.SQLException} so callers don't have
 * to handle checked exceptions in lambdas.
 */
public class QueryException extends RuntimeException {

    public QueryException(String message, Throwable cause) {
        super(message, cause);
    }
}