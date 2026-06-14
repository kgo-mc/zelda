package net.kgomc.zelda.database.serialization;

import net.kgomc.zelda.core.context.ZeldaContext;
import net.kgomc.zelda.database.serialization.annotations.Column;
import net.kgomc.zelda.database.serialization.annotations.Transient;

import java.lang.reflect.Field;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.function.Supplier;

final class FieldCache {
    Field[] fields;  // 1-indexed; null slot = no matching field for that column

    <T> void resolveIfNeeded(Supplier<T> factory, ResultSetMetaData meta,
                             int cols, MappingStrategy strategy) throws SQLException {
        if (fields != null) return;

        T probe = factory.get();
        fields  = new Field[cols + 1];

        for (int i = 1; i <= cols; i++) {
            String columnLabel = meta.getColumnLabel(i);
            Field  field       = findField(probe.getClass(), columnLabel);

            if (field != null) {
                field.setAccessible(true);
            } else {
                switch (strategy) {
                    case STRICT  -> throw new MappingException(
                            "No field for column '" + columnLabel + "' on " + probe.getClass().getName() +
                                    ". Use an SQL alias, add the field, or annotate with @Column.", null);
                    case LENIENT -> ZeldaContext.get().getLogger().warning(
                            "[Zelda/DB] Unmapped column '" + columnLabel +
                                    "' on " + probe.getClass().getName() + " — skipping.");
                    case SILENT  -> {} // intentional no-op
                }
            }

            fields[i] = field; // null slots are safe; row loop checks them
        }
    }

    private static String toCamelCase(String column) {
        if (!column.contains("_")) return column;
        StringBuilder sb = new StringBuilder();
        boolean nextUpper = false;
        for (char c : column.toCharArray()) {
            if (c == '_') { nextUpper = true; }
            else if (nextUpper) { sb.append(Character.toUpperCase(c)); nextUpper = false; }
            else { sb.append(c); }
        }
        return sb.toString();
    }

    /**
     * Resolves the field for a given column.
     * Priority:
     *  1) a field annotated {@code @Column(columnLabel)} — exact match wins immediately
     *  2) a field matching the snake_case → camelCase convention
     * Fields annotated {@code @Transient}, or {@code @Column} for a *different*
     * column, are never used as convention fallbacks.
     */
    private static Field findField(Class<?> type, String columnLabel) {
        String camelCaseName = toCamelCase(columnLabel);

        Class<?> current = type;
        while (current != null && current != Object.class) {
            Field byConvention = null;

            for (Field f : current.getDeclaredFields()) {
                if (f.isAnnotationPresent(Transient.class)) continue;

                Column col = f.getAnnotation(Column.class);
                if (col != null) {
                    if (col.value().equals(columnLabel)) return f; // explicit match wins
                    continue; // claimed for a different column — skip entirely
                }

                if (byConvention == null && f.getName().equals(camelCaseName)) {
                    byConvention = f;
                }
            }

            if (byConvention != null) return byConvention;
            current = current.getSuperclass();
        }
        return null;
    }
}
