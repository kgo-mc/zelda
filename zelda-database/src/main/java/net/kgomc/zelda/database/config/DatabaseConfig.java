package net.kgomc.zelda.database.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Represents the database connection configuration, deserialised from a JSON file.
 *
 * <p>Example {@code database.json} for PostgreSQL:</p>
 * <pre>{@code
 * {
 *   "type": "POSTGRESQL",
 *   "host": "localhost",
 *   "port": 5432,
 *   "database": "mc_server",
 *   "username": "zelda",
 *   "password": "secret",
 *   "pool": {
 *     "maximumPoolSize": 10,
 *     "minimumIdle": 2,
 *     "connectionTimeout": 30000,
 *     "idleTimeout": 600000,
 *     "maxLifetime": 1800000
 *   }
 * }
 * }</pre>
 *
 * <p>Example for SQLite (host/port/username/password are ignored):</p>
 * <pre>{@code
 * {
 *   "type": "SQLITE",
 *   "database": "data/zelda.db"
 * }
 * }</pre>
 */
public final class DatabaseConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    @SerializedName("type")
    private DatabaseType type;

    @SerializedName("host")
    private String host = "localhost";

    @SerializedName("port")
    private int port = -1; // -1 = use driver default

    @SerializedName("database")
    private String database;

    @SerializedName("username")
    private String username;

    @SerializedName("password")
    private String password;

    @SerializedName("pool")
    private PoolConfig pool = new PoolConfig();

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    /**
     * Loads a {@link DatabaseConfig} from the given JSON file path.
     *
     * @param path absolute or relative path to the JSON file
     * @return parsed config
     * @throws IOException              if the file cannot be read
     * @throws IllegalArgumentException if required fields are missing
     */
    public static DatabaseConfig load(Path path) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            DatabaseConfig config = GSON.fromJson(reader, DatabaseConfig.class);
            config.validate();
            return config;
        }
    }

    private void validate() {
        if (type == null) throw new IllegalArgumentException("database.json: 'type' is required (POSTGRESQL, MYSQL, SQLITE)");
        if (database == null || database.isBlank()) throw new IllegalArgumentException("database.json: 'database' is required");
        if (type != DatabaseType.SQLITE) {
            if (username == null || username.isBlank()) throw new IllegalArgumentException("database.json: 'username' is required for " + type);
        }
    }


    public String buildJdbcUrl() {
        return switch (type) {
            case POSTGRESQL -> String.format("jdbc:postgresql://%s:%d/%s",
                    host, port == -1 ? 5432 : port, database);
            case MYSQL -> String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true",
                    host, port == -1 ? 3306 : port, database);
            case SQLITE -> "jdbc:sqlite:" + database;
        };
    }

    public String driverClassName() {
        return switch (type) {
            case POSTGRESQL -> "org.postgresql.Driver";
            case MYSQL      -> "com.mysql.cj.jdbc.Driver";
            case SQLITE     -> "org.sqlite.JDBC";
        };
    }

    public DatabaseType getType()    { return type; }
    public String getHost()          { return host; }
    public int getPort()             { return port; }
    public String getDatabase()      { return database; }
    public String getUsername()      { return username; }
    public String getPassword()      { return password; }
    public PoolConfig getPool()      { return pool; }


    public static final class PoolConfig {
        @SerializedName("maximumPoolSize")  private int maximumPoolSize  = 10;
        @SerializedName("minimumIdle")      private int minimumIdle      = 2;
        @SerializedName("connectionTimeout")private long connectionTimeout = 30_000;
        @SerializedName("idleTimeout")      private long idleTimeout      = 600_000;
        @SerializedName("maxLifetime")      private long maxLifetime      = 1_800_000;

        public int  getMaximumPoolSize()   { return maximumPoolSize; }
        public int  getMinimumIdle()       { return minimumIdle; }
        public long getConnectionTimeout() { return connectionTimeout; }
        public long getIdleTimeout()       { return idleTimeout; }
        public long getMaxLifetime()       { return maxLifetime; }
    }
}