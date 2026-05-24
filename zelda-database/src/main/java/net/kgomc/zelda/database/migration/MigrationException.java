package net.kgomc.zelda.database.migration;

/**
 * Thrown when a migration fails to apply.
 * The failed migration is always rolled back before this is thrown.
 */
public class MigrationException extends RuntimeException {
    public MigrationException(String message, Throwable cause) {
        super(message, cause);
    }
}