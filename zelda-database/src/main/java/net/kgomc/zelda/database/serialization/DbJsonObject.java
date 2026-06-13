package net.kgomc.zelda.database.serialization;

import net.kgomc.zelda.core.serialization.ZeldaGson;
import org.postgresql.util.PGobject;

/**
 * Represents an abstraction for serializing and deserializing objects to and from
 * database-compliant JSON representations. This interface provides methods for
 * converting objects to a database JSON format as well as parsing JSON strings
 * back into Java objects of a specified type.
 *
 */
public interface DbJsonObject {

    /**
     * A constant representation of a JSON serializer and deserializer that is
     * tailored for PostgreSQL's `jsonb` data type. This instance leverages the
     * `PostgresJsonb` implementation to transform Java objects into PostgreSQL
     * `jsonb`-compliant objects and parse `jsonb`-formatted strings back into
     * Java objects of a given type.
     *
     * This constant provides a ready-to-use implementation of the {@link DbJsonObject}
     * interface for PostgreSQL integrations.
     */
    public static final DbJsonObject POSTGRES = new PostgresJsonb();

    /**
     * A predefined, immutable instance of {@link DbJsonObject} that provides functionality for
     * serializing Java objects into SQLite-compatible JSON format and deserializing JSON strings
     * back into Java objects.
     *
     * This constant leverages the {@code SqliteJson} implementation to facilitate seamless translation
     * for SQLite database interactions. It uses the `ZeldaGson` library to handle JSON serialization
     * and deserialization processes.
     */
    public static final DbJsonObject SQLITE = new SqliteJson();

    /**
     * Converts the given object into a database-compliant representation.
     * The implementation determines how the object is serialized, ensuring
     * compatibility with the target database's data format.
     *
     * @param o the object to be transformed into a database-compliant format.
     *          This may include basic types, complex objects, or collections.
     * @return the database-compliant representation of the provided object.
     *         This can be used for storing the object in the database.
     */
    Object toDb(Object o);

    /**
     * Deserializes a JSON string into an object of the specified class type.
     *
     * @param <T>   the type of the object to be returned
     * @param json  the JSON string representing the object
     * @param clazz the class of the object to create from the JSON string
     * @return an instance of the specified class type created from the JSON string
     *         or null if the JSON string cannot be parsed into the given type
     */
    <T> T fromDb(String json, Class<T> clazz);

}

final class PostgresJsonb implements DbJsonObject {

    @Override
    public Object toDb(Object o) {
        try {
            PGobject obj = new PGobject();
            obj.setType("jsonb");
            obj.setValue(ZeldaGson.toJson(o));
            return obj;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public <T> T fromDb(String json, Class<T> clazz) {
        return ZeldaGson.fromJson(json, clazz);
    }

}

final class SqliteJson implements DbJsonObject {

    @Override
    public Object toDb(Object o) {
        return ZeldaGson.toJson(o);
    }

    @Override
    public <T> T fromDb(String json, Class<T> clazz) {
        return ZeldaGson.fromJson(json, clazz);
    }
}