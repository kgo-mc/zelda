# zelda-database

HikariCP-backed database module. Supports PostgreSQL, MySQL, and SQLite. Provides an async query builder, schema migrations with distributed locking, row-level and advisory locking, and reactive RxJava wrappers.

---

## Setup

Config lives in `database.json` in your central config dir (or plugin data folder):

```json
{
  "type": "POSTGRESQL",
  "host": "localhost",
  "port": 5432,
  "database": "mc_server",
  "username": "zelda",
  "password": "secret",
  "pool": {
    "maximumPoolSize": 10,
    "minimumIdle": 2
  }
}
```

```java
Zelda.builder()
    .withDatabase()
    .initialize(adapter);

DatabaseModule db = Zelda.modules().find(DatabaseModule.class).orElseThrow();
QueryRunner runner = db.getRunner();
```

---

## Querying

```java
// Sync — list
List<PlayerData> players = runner.query(
    "SELECT * FROM players WHERE coins > ?",
    ResultSerializer.toObject(PlayerData.class),
    100
);

// Sync — single
Optional<PlayerData> player = runner.queryOne(
    "SELECT * FROM players WHERE uuid = ?",
    ResultSerializer.toObject(PlayerData.class),
    uuid.toString()
);

// Update
int rows = runner.update("UPDATE players SET coins = ? WHERE uuid = ?", coins, uuid);
```

## Reactive (RxJava)

```java
runner.queryOneRx("SELECT * FROM players WHERE uuid = ?",
        ResultSerializer.toObject(PlayerData.class), uuid.toString())
    .subscribeOn(ZeldaSchedulers.io())
    .observeOn(ZeldaSchedulers.serverThread())
    .subscribe(
        data -> player.sendMessage("Coins: " + data.coins),
        error -> logger.severe("DB error: " + error.getMessage())
    );
```

## Transactions

```java
runner.transaction(conn -> {
    runner.update(conn, "INSERT INTO log VALUES (?)", "login");
    runner.update(conn, "UPDATE players SET last_seen = NOW() WHERE uuid = ?", uuid);
});

// Reactive
runner.transactionRx(conn -> {
    runner.update(conn, "UPDATE players SET coins = coins - ? WHERE uuid = ?", cost, uuid);
}).subscribeOn(ZeldaSchedulers.io()).subscribe();
```

---

## Migrations

```java
public class V1_CreatePlayersTable implements IMigration {

    @Override public int getVersion()         { return 1; }
    @Override public String getDescription()  { return "Create players table"; }

    @Override
    public void migrate(Connection conn, DatabaseType type) throws Exception {
        try (Statement st = conn.createStatement()) {
            st.execute("""
                CREATE TABLE IF NOT EXISTS players (
                    uuid  VARCHAR(36) PRIMARY KEY,
                    coins INT NOT NULL DEFAULT 0
                )
            """);
        }
    }
}

// Run (protected by distributed advisory lock)
db.migrations()
    .register(new V1_CreatePlayersTable())
    .register(new V2_AddCoinsIndex())
    .run();
```

The `DatabaseType` parameter lets you write DB-specific SQL where needed (e.g. `JSONB` on PostgreSQL).

---

## Locking

```java
LockManager locks = db.getLockManager();

// Advisory lock — distributed mutex across nodes
try (ZeldaLock lock = locks.advisory("zelda:daily-reset", 30)) {
    // only one node runs this at a time
    resetDailyRewards();
}

// Row lock — SELECT FOR UPDATE inside a transaction
runner.transaction(conn -> {
    locks.forUpdate(conn, "players", "uuid = ?", uuid);
    int coins = runner.queryOne(conn, "SELECT coins FROM players WHERE uuid = ?",
        ResultSerializer.toInt("coins"), uuid).orElse(0);
    runner.update(conn, "UPDATE players SET coins = ? WHERE uuid = ?", coins + 100, uuid);
});
```

---

## ResultSerializer

```java
ResultSerializer.toObject(PlayerData.class) // POJO via ZeldaGson
ResultSerializer.toMap()                     // Map<String, Object>
ResultSerializer.toString("name")            // single String column
ResultSerializer.toInt("coins")              // single int column
ResultSerializer.toUUID("uuid")              // UUID from string column
ResultSerializer.fromJson("data", Config.class) // JSON column → POJO
ResultSerializer.toJson(myObject)            // POJO → JSON string for insert
```

---

## Dependencies

- `zelda-core`
- `com.zaxxer:HikariCP`
- `com.google.code.gson:gson`
- `org.postgresql:postgresql` (optional)
- `com.mysql:mysql-connector-j` (optional)
- `org.xerial:sqlite-jdbc` (optional)