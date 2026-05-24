# zelda-outbox

Transactional outbox pattern + RxJava event bus. Persist events atomically to the database inside a transaction, then poll and dispatch them reliably with exponential backoff retry and dead-letter support. Multiple server nodes are safe — the poller uses an advisory lock per batch.

---

## How it works

```
write to DB + publish event (same transaction)
  └── zelda_outbox table (PENDING)
        └── poller picks up every N seconds (advisory locked)
              └── dispatch to handlers
                    ├── success → mark DONE
                    └── failure → backoff retry (1s, 2s, 4s...)
                          └── max attempts exceeded → move to zelda_outbox_dead
```

---

## Setup

Migration runs automatically — no manual setup needed.

```java
Zelda.builder()
    .withDatabase()
    .withOutbox()               // defaults: 5s poll, batch 50, 3 max attempts
    .initialize(adapter);

// Custom settings
Zelda.builder()
    .withOutbox(10, 100, 5)    // pollSeconds, batchSize, maxAttempts
    .initialize(adapter);

OutboxModule outbox = Zelda.modules().find(OutboxModule.class).orElseThrow();
```

---

## Publishing

```java
// Inside a transaction — atomically safe
runner.transaction(conn -> {
    runner.update(conn,
        "UPDATE players SET coins = coins - ? WHERE uuid = ?", cost, uuid);
    outbox.publish(conn, "player.purchase",
        Map.of("uuid", uuid.toString(), "item", "sword", "cost", cost));
});

// Standalone transaction
outbox.publish("player.login",
    Map.of("uuid", uuid.toString(), "name", playerName));

// Delayed
outbox.publishDelayed("player.reminder", payload, Duration.ofMinutes(5));
```

---

## Subscribing

```java
// Synchronous — runs on IO thread
outbox.subscribe("player.purchase", event -> {
    UUID uuid = event.getUUID("uuid");
    int cost  = event.getInt("cost");
    receiptService.record(uuid, cost);
});

// Async — process on IO thread, deliver result on server main thread
outbox.subscribeAsync("player.purchase",
    event -> receiptService.generate(event),      // IO thread
    event -> player.sendMessage("Receipt ready!") // server thread
);

// Raw Observable — full RxJava control
outbox.observe("player.purchase")
    .filter(e -> e.getInt("cost") > 1000)
    .subscribeOn(ZeldaSchedulers.io())
    .observeOn(ZeldaSchedulers.serverThread())
    .subscribe(event -> { ... });
```

---

## Reading event payload

```java
event.getString("item")          // String
event.getInt("cost")             // int
event.getLong("timestamp")       // long
event.getDouble("price")         // double
event.getBoolean("vip")          // boolean
event.getUUID("uuid")            // UUID
event.getObject("details", PurchaseDetails.class) // POJO via ZeldaGson
event.getRawPayload()            // raw JSON string
```

---

## Dead letter table

Events that exceed `maxAttempts` are moved to `zelda_outbox_dead` with the last error message. Query them to diagnose failures:

```sql
SELECT * FROM zelda_outbox_dead ORDER BY failed_at DESC;
```

---

## PostgreSQL payload

On PostgreSQL the `payload` column is `JSONB` — you can query events by payload content:

```sql
-- Find all purchase events over 1000 coins
SELECT * FROM zelda_outbox
WHERE event_type = 'player.purchase'
  AND (payload->>'cost')::int > 1000;
```

---

## Dependencies

- `zelda-core`
- `zelda-database`
- `io.reactivex.rxjava3:rxjava`
- `com.google.code.gson:gson`