# zelda-bus

Type-safe cross-server event bus and RPC over NATS. Plugins expose typed `ZeldaEventDef` and `ZeldaRpcDef` contracts in a separate API artifact that consumers depend on — giving full compile-time safety with no stringly-typed topic names.

---

## Infrastructure

```bash
# Start NATS (and PostgreSQL) locally
docker compose up -d

# Monitor NATS
open http://localhost:8222

# Debug pub/sub with NATS CLI
docker compose --profile tools run --rm nats-cli
> sub "economy.>"          # watch all economy events
> pub "economy.test" '{}'  # publish a test event
```

---

## Config (`zelda-bus.yml`)

```yaml
server-id: "lobby-1"    # unique per server instance

nats:
  url: "nats://localhost:4222"
  connection-timeout-seconds: 5
  max-reconnects: -1
  reconnect-wait-ms: 1000
```

---

## Setup

```java
Zelda.builder()
    .centralConfig(Path.of("plugins/Zelda"))
    .withBus()
    .initialize(adapter);

BusModule bus = Zelda.modules().find(BusModule.class).orElseThrow();
ZeldaEventBus events = bus.getEventBus();
ZeldaRpc       rpc   = bus.getRpc();
```

---

## Defining a typed API (in your plugin's API module)

```java
// EconomyEvents.java
public final class EconomyEvents {
    public static final ZeldaEventDef<CoinsUpdatedEvent> COINS_UPDATED =
        ZeldaEventDef.of("economy.coins_updated", CoinsUpdatedEvent.class);
}

// EconomyRpc.java
public final class EconomyRpc {
    public static final ZeldaRpcDef<GetCoinsRequest, GetCoinsResponse> GET_COINS =
        ZeldaRpcDef.of("economy", "getCoins", GetCoinsRequest.class, GetCoinsResponse.class);
}

// Payload records
public record CoinsUpdatedEvent(UUID uuid, int oldCoins, int newCoins, String reason) {}
public record GetCoinsRequest(UUID uuid) {}
public record GetCoinsResponse(UUID uuid, int coins) {}
```

---

## Publishing events

```java
// Broadcast — all servers receive it
bus.publish(EconomyEvents.COINS_UPDATED, payload).send();            // broadcast
bus.publish(DungeonEvents.INSTANCE_READY, payload).to("dungeon-3").send(); // targeted
bus.publish(DungeonEvents.ROUND_START, payload).toAll().send();      // explicit broadcast
```

---

## Subscribing to events

```java
events.on(EconomyEvents.COINS_UPDATED)           // Observable<EventMessage<CoinsUpdatedEvent>>
      .map(EventMessage::payload)                // unwrap to CoinsUpdatedEvent
      .filter(e -> e.newCoins() > e.oldCoins())  // fully typed
      .observeOn(ZeldaSchedulers.serverThread())
      .subscribe(e -> scoreboard.update(e.uuid(), e.newCoins()));

// Filter by source server
events.on(EconomyEvents.COINS_UPDATED)
      .filter(msg -> msg.isFrom("lobby-1"))
      .subscribe(msg -> { ... });
```

---

## RPC — registering a handler

```java
// Exactly one server handles each request (NATS queue group)
rpc.handle(EconomyRpc.GET_COINS, req ->
    Single.fromCallable(() ->
        new GetCoinsResponse(req.uuid(), economy.getCoins(req.uuid()))
    )
);
```

---

## RPC — calling

```java
// Load-balanced — any handler instance responds
rpc.call(EconomyRpc.GET_COINS, new GetCoinsRequest(uuid))
   .subscribe(
       resp -> player.sendMessage("Coins: " + resp.coins()),
       err  -> player.sendMessage("Failed: " + err.getMessage())
   );

// Targeted — must run on "lobby-1"
rpc.call(EconomyRpc.GET_COINS, new GetCoinsRequest(uuid))
   .on("lobby-1")
   .subscribe(resp -> { ... });

// Custom timeout
rpc.call(EconomyRpc.GET_COINS, req)
   .timeout(Duration.ofSeconds(10))
   .subscribe(resp -> { ... });

// Sync (IO thread only — never call on server main thread)
GetCoinsResponse resp = rpc.call(EconomyRpc.GET_COINS, req).toSync();
```

---

## Routing summary

| Call | NATS subject | Who handles it |
|---|---|---|
| `rpc.call(def, req)` | `rpc.economy.getCoins` | One server (queue group) |
| `rpc.call(def, req).on("lobby-1")` | `rpc.economy.getCoins.lobby-1` | Only lobby-1 |
| `events.publish(def, payload)` | `economy.coins_updated` | All subscribers |
| `events.publish(def, payload).to("lobby-1")` | `economy.coins_updated.lobby-1` | Only lobby-1 |

---

## Dependencies

- `zelda-core`
- `io.nats:jnats`
- `io.reactivex.rxjava3:rxjava`
- `com.google.code.gson:gson`