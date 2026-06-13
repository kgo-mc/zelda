package net.kgomc.zelda.database.conventions;

import javax.annotation.Nonnull;
import java.util.Locale;

public class PostgresConventions implements IConventions {

    @Override
    public String tableName(@Nonnull String schema, @Nonnull String table) {
        return tableName(schema) + "." + tableName(table);
    }

    @Override
    public String tableName(@Nonnull String table) {
        return toSnakeCase(table);
    }

    @Override
    public String columnName(@Nonnull String column) {
        return toSnakeCase(column);
    }

    private String toSnakeCase(@Nonnull String value) {
        return value
                .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
                .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
                .toLowerCase(Locale.ROOT);
    }
}