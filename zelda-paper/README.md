# zelda-paper

Adapts Paper/Spigot/Bukkit's `JavaPlugin` to the `ZeldaPlugin` interface. The only Zelda module that imports Paper — all other modules remain platform-agnostic through `ZeldaPlugin`.

---

## Usage

```java
public class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        Zelda.builder()
            .withDatabase()
            .initialize(new PaperPluginAdapter(this));
    }

    @Override
    public void onDisable() {
        Zelda.shutdown();
    }
}
```

All values are derived from the `JavaPlugin` automatically — name, logger, and data folder. Override any of them with the fluent API if needed:

```java
new PaperPluginAdapter(this)
    .withName("CustomName")
    .withDataFolder(someOtherPath)
    .withLogger(myCustomLogger)
```

---

## Scheduling

`runSync()` and `runTaskTimer()` delegate to Bukkit's scheduler:

```java
// Runs on server main thread — safe to call Bukkit API
context.getPlugin().runSync(() -> player.sendMessage("Hello!"));

// Repeating task
ZeldaTask task = context.getPlugin().runTaskTimer(this::tick, 0L, 20L);
task.cancel(); // when done
```

---

## Dependencies

- `zelda-core`
- `io.papermc.paper:paper-api` (provided)