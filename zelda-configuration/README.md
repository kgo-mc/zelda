# zelda-configuration

Annotation-driven YAML configuration module built on ConfigLib. Supports multiple config files, subdirectory paths, file-level headers, hot-reload, and per-file property customisation. Uses Bukkit type serializers on Paper (ItemStack, Location, etc.) and plain YAML on Velocity.

---

## Defining a config

```java
@ZeldaConfig("settings.yml")
@ConfigHeader({
    "Server Settings",
    "Restart required for most changes."
})
@Configuration
public class SettingsConfig {

    @Comment("Server display name shown in menus")
    public String serverName = "KGO MC";

    @Comment("Maximum players in the lobby")
    public int maxLobbyPlayers = 16;
}

// Subdirectory
@ZeldaConfig(value = "economy.yml", path = "modules/economy")
@Configuration
public class EconomyConfig {
    public int startingCoins = 100;
}
```

---

## Setup

```java
Zelda.builder()
    .withConfiguration()
    .initialize(adapter);

ConfigurationModule cfg = Zelda.modules().find(ConfigurationModule.class).orElseThrow();
cfg.getRegistry().register(SettingsConfig.class);
cfg.getRegistry().register(EconomyConfig.class);
```

---

## Reading

```java
SettingsConfig settings = cfg.getRegistry().get(SettingsConfig.class);
String name = settings.serverName;
```

---

## Hot reload

```java
// Reload all configs — thread-safe, takes write lock
cfg.getRegistry().reloadAll();

// Reload one
cfg.getRegistry().reload(SettingsConfig.class);
```

---

## Per-file property override

```java
cfg.getRegistry().register(EconomyConfig.class, props -> props
    .toBuilder()
    .outputNulls(true)
    .build()
);
```

---

## Path resolution

| Scenario | File location |
|---|---|
| `@ZeldaConfig("settings.yml")` | `baseDir/settings.yml` |
| `@ZeldaConfig(value = "eco.yml", path = "modules")` | `baseDir/modules/eco.yml` |
| `.withConfiguration(customPath)` | `customPath/settings.yml` |
| `.centralConfig(path)` set | `centralPath/settings.yml` |

---

## Dependencies

- `zelda-core`
- `com.github.Exlll.ConfigLib:configlib-paper`