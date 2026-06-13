package net.kgomc.zelda.database.serialization;

public enum MappingStrategy {
    /** Throws if a column has no matching field. Catches bugs early. */
    STRICT,
    /** Logs a warning and skips unmapped columns. */
    LENIENT,
    /** Silently skips unmapped columns. Current behaviour. */
    SILENT
}
