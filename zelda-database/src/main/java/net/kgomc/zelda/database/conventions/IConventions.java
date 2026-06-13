package net.kgomc.zelda.database.conventions;

import javax.annotation.Nonnull;

/**
 * Defines naming conventions for database schemas, tables, and columns.
 * Provides methods for generating table names based on schema, entity classes,
 * and other parameters, as well as methods for column name conventions.
 */
public interface IConventions {

    /**
     * Generates a fully-qualified table name by combining the provided schema and table name.
     *
     * @param schema the schema name, must not be null.
     * @param table the table name within the schema, must not be null.
     * @return the fully-qualified table name constructed from the schema and table names.
     */
    String tableName(@Nonnull String schema, @Nonnull String table);

    /**
     * Generates a table name based on the provided table string.
     *
     * @param table the name of the table, must not be null.
     * @return the table name string.
     */
    String tableName(@Nonnull String table);

    /**
     * Generates a fully-qualified table name by combining the provided schema and a
     * transformed name derived from the simple name of the given entity class.
     * The entity class's name is converted to lowercase and underscores are inserted
     * before capital letters, excluding the first character.
     *
     * @param schema the schema name, must not be null.
     * @param entityClass the class of the entity, must not be null.
     * @return the fully-qualified table name constructed from the schema and the transformed entity class name.
     */
    default String tableName(@Nonnull String schema, @Nonnull Class<?> entityClass){
        return tableName(schema, entityClass.getSimpleName());
    }

    /**
     * Generates a table name by transforming the simple name of the provided entity class.
     * The entity class's name is converted to lowercase, and underscores are added before
     * each capital letter, excluding the first character.
     *
     * @param entityClass the class of the entity, must not be null.
     * @return the transformed table name derived from the entity class name.
     */
    default String tableName(@Nonnull Class<?> entityClass){
        return tableName(entityClass.getSimpleName());
    }

    /**
     * Generates a column name based on the provided column string.
     *
     * @param column the name of the column, must not be null.
     * @return the column name string.
     */
    String columnName(@Nonnull String column);

    /**
     * A static constant representing the PostgreSQL-specific implementation of the
     * {@link IConventions} interface. This implementation defines the naming conventions
     * used in PostgreSQL databases, including methods for generating table and column names
     * based on schemas, entities, and other parameters.
     */
    static final IConventions POSTGRES = new PostgresConventions();

}
