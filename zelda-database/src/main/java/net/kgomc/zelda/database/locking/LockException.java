package net.kgomc.zelda.database.locking;

/**
 * Thrown when a lock cannot be acquired or released.
 */
public class LockException extends RuntimeException {

    public LockException(String message) {
        super(message);
    }

    public LockException(String message, Throwable cause) {
        super(message, cause);
    }
}