# zelda-injection

Lightweight JSR-330 dependency injection backed by [Feather](https://github.com/zsoltherpai/feather). Auto-binds all active Zelda modules after `onEnable()` completes. Supports constructor, field, and method injection plus `@Singleton` scoping.

---

## Setup

```java
Zelda.builder()
    .withDatabase()
    .withMessaging()
    .withInjection(binder -> binder
        .bind(EconomyService.class, EconomyServiceImpl.class)
        .bindInstance(ServerConfig.class, myConfig)
        .bindSupplier(DataSource.class, () -> pool.getSource())
    )
    .initialize(adapter);
```

---

## Getting instances

```java
ZeldaInjector injector = (ZeldaInjector) ZeldaContext.get().getInjector();

EconomyService economy = injector.get(EconomyService.class);
DatabaseModule db       = injector.get(DatabaseModule.class); // auto-bound
MessagingModule msg     = injector.get(MessagingModule.class); // auto-bound
```

---

## Injecting into existing objects

Use `injectMembers()` for objects you construct yourself (command handlers, listeners, etc.):

```java
public class ShopCommand {
    @Inject private EconomyService economy;
    @Inject private MessagingModule messaging;
}

ShopCommand cmd = new ShopCommand();
injector.injectMembers(cmd); // fills @Inject fields
```

---

## Constructor injection (preferred)

Feather handles this automatically for any class with a single `@Inject`-annotated constructor:

```java
public class EconomyServiceImpl implements EconomyService {

    private final QueryRunner runner;
    private final MessagingModule messaging;

    @Inject
    public EconomyServiceImpl(QueryRunner runner, MessagingModule messaging) {
        this.runner    = runner;
        this.messaging = messaging;
    }
}
```

---

## Auto-bound by default

No declaration needed for these:

| Type | Bound to |
|---|---|
| `ZeldaContext` | Active context |
| `ZeldaPlugin` | Platform adapter |
| `Logger` | Plugin logger |
| `Path` | Plugin data folder |
| `DatabaseModule` | If `withDatabase()` was used |
| `MessagingModule` | If `withMessaging()` was used |
| `ConfigurationModule` | If `withConfiguration()` was used |
| `UIModule` | If `withUI()` was used |
| `OutboxModule` | If `withOutbox()` was used |

---

## Binding types

```java
// Interface → implementation class (Feather handles constructor injection)
binder.bind(EconomyService.class, EconomyServiceImpl.class)

// Pre-existing instance (always singleton)
binder.bindInstance(Config.class, myConfig)

// Supplier factory (singleton by default)
binder.bindSupplier(DataSource.class, () -> createDataSource())

// Supplier factory (new instance per get())
binder.bindSupplier(Report.class, Report::new, false)
```

---

## Two-phase initialisation

The injector is built **after** all modules complete `onEnable()`, not at builder time. This guarantees modules are fully initialised before they're registered as bindings. Internally, `InjectionModule` implements `LifecycleHook.afterAllEnabled()` for this.

---

## Dependencies

- `zelda-core`
- `com.github.zsoltherpai:feather`
- `javax.inject:javax.inject`