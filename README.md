# Zelda

A modular, platform-agnostic library for Minecraft plugin development. Built for **Paper** and **Velocity**, Zelda provides a consistent foundation across your entire server network — shared database connections, palette-driven messaging, inventory UIs, dependency injection, and a transactional outbox event bus.

---

## Modules

| Module | Description |
|---|---|
| [`zelda-core`](zelda-core/README.md) | Platform abstraction, module lifecycle, RxJava schedulers, shared Gson |
| [`zelda-paper`](zelda-paper/README.md) | Adapts `JavaPlugin` to `ZeldaPlugin` |
| [`zelda-velocity`](zelda-velocity/README.md) | Adapts Velocity's plugin system to `ZeldaPlugin` |
| [`zelda-database`](zelda-database/README.md) | HikariCP connection pooling, query builder, migrations, locking |
| [`zelda-configuration`](zelda-configuration/README.md) | Annotation-driven YAML config, hot-reload, multi-file |
| [`zelda-messaging`](zelda-messaging/README.md) | Palette-driven MiniMessage formatting, templates, multi-channel delivery |
| [`zelda-ui`](zelda-ui/README.md) | Config-driven inventory UIs built on InvUI |
| [`zelda-injection`](zelda-injection/README.md) | Lightweight JSR-330 dependency injection backed by Feather |
| [`zelda-outbox`](zelda-outbox/README.md) | Transactional outbox pattern + RxJava event bus |
| [`zelda-builder`](zelda-builder/README.md) | Entry point — `Zelda.builder()` fluent API |

---

## Quick Start

### Paper

```xml
<dependency>
    <groupId>net.kgomc.zelda</groupId>
    <artifactId>zelda-builder</artifactId>
    <version>1.0.0</version>
</dependency>
```

```java
public class MyPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        Zelda.builder()
            .centralConfig(Path.of("plugins/Zelda"))
            .withDatabase()
            .withConfiguration()
            .withMessaging()
            .withUI()
            .withOutbox()
            .withInjection(binder -> binder
                .bind(EconomyService.class, EconomyServiceImpl.class)
            )
            .initialize(new PaperPluginAdapter(this));
    }

    @Override
    public void onDisable() {
        Zelda.shutdown();
    }
}
```

### Velocity

```java
@Plugin(id = "myplugin", name = "MyPlugin", version = "1.0.0")
public class MyPlugin {

    @Inject
    public MyPlugin(ProxyServer server, PluginContainer container,
                    Logger logger, @DataDirectory Path dataDirectory) {
        Zelda.builder()
            .centralConfig(Path.of("plugins/Zelda"))
            .withDatabase()
            .withMessaging()
            .initialize(new VelocityPluginAdapter(container, logger, dataDirectory, server));
    }
}
```

---

## Central Config

Set `.centralConfig(path)` to share a single config directory across all your plugins on the same server. This means one `palette.yml`, one `database.json`, and one `messages.yml` for the entire network.

```
plugins/
└── Zelda/
    ├── database.json     ← shared DB connection
    ├── palette.yml       ← shared color palette
    └── messages.yml      ← shared message templates
```

---

## Accessing Modules

After `initialize()` completes, any module is accessible anywhere:

```java
// Direct registry lookup
DatabaseModule db = Zelda.modules().find(DatabaseModule.class).orElseThrow();
MessagingModule msg = Zelda.modules().find(MessagingModule.class).orElseThrow();

// Via injector (if withInjection() was used)
ZeldaInjector injector = (ZeldaInjector) ZeldaContext.get().getInjector();
EconomyService economy = injector.get(EconomyService.class);
```

---

## Requirements

- Java 21+
- Paper 1.21.4+ (for Paper plugins)
- Velocity 3.3+ (for Velocity plugins)

---

## Installing

See [RELEASING.md](RELEASING.md) for how to pull Zelda from GitHub Packages.

---

## License

MIT