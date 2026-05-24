# zelda-velocity

Adapts Velocity's plugin system to the `ZeldaPlugin` interface. The only Zelda module that imports Velocity — all other modules remain platform-agnostic through `ZeldaPlugin`. Bridges Velocity's SLF4J logger to `java.util.logging` internally.

---

## Usage

```java
@Plugin(id = "myplugin", name = "MyPlugin", version = "1.0.0")
public class MyPlugin {

    @Inject
    public MyPlugin(ProxyServer server, PluginContainer container,
                    Logger logger, @DataDirectory Path dataDirectory) {
        Zelda.builder()
            .withDatabase()
            .withMessaging()
            .initialize(new VelocityPluginAdapter(container, logger, dataDirectory, server));
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        Zelda.shutdown();
    }
}
```

All values are derived from Velocity's injected values automatically. Override any of them with the fluent API:

```java
new VelocityPluginAdapter(container, logger, dataDirectory, server)
    .withName("CustomName")
    .withDataFolder(someOtherPath)
    .withLogger(myCustomJulLogger)
```

---

## Platform notes

- **No main thread** — `runSync()` runs the task immediately inline. Velocity event handling is thread-safe by design.
- **Tick-based scheduling** — `runTaskTimer()` converts ticks to milliseconds (50ms/tick) and uses Velocity's built-in scheduler.
- **UI not supported** — `UIModule` throws on `RuntimeKind.PROXY`. Inventory UIs are server-side only.
- **Boss bar and sound** — `VelocityPlayerTarget` silently ignores these channels; they require a backend server.

---

## Dependencies

- `zelda-core`
- `com.velocitypowered:velocity-api` (provided)