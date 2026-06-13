package net.kgomc.zelda.database.serialization;

import net.kgomc.zelda.core.context.ZeldaContext;

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
            String fieldName   = toCamelCase(columnLabel);
            Field  field       = findField(probe.getClass(), fieldName);

            if (field != null) {
                field.setAccessible(true);
            } else {
                switch (strategy) {
                    case STRICT  -> throw new MappingException(
                            "No field '" + fieldName + "' on " + probe.getClass().getName() +
                                    " for column '" + columnLabel + "'. Use an SQL alias or add the field.", null);
                    case LENIENT -> ZeldaContext.get().getLogger().warning(
                            "[Zelda/DB] Unmapped column '" + columnLabel + "' (-> '" + fieldName +
                                    "') on " + probe.getClass().getName() + " — skipping.");
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

    // Walks the class hierarchy so inherited fields also resolve
    private static Field findField(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try { return current.getDeclaredField(name); }
            catch (NoSuchFieldException e) { current = current.getSuperclass(); }
        }
        return null;
    }
}
