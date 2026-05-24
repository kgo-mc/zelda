package net.kgomc.zelda.database.query;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Maps a single row of a {@link ResultSet} to an object of type {@code T}.
 *
 * <p>Implement this as a lambda or method reference:</p>
 * <pre>{@code
 * RowMapper<Player> mapper = rs -> new Player(
 *     rs.getString("uuid"),
 *     rs.getString("name"),
 *     rs.getInt("coins")
 * );
 * }</pre>
 *
 * @param <T> the target type
 */
@FunctionalInterface
public interface RowMapper<T> {
    T map(ResultSet rs) throws SQLException;
}