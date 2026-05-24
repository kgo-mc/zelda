# zelda-messaging

Palette-driven MiniMessage messaging. Define semantic color names once in `palette.yml` and reference them as `<primary>`, `<error>`, `<rainbow>` etc. in any message string. Supports per-player theme overrides, named templates, gradient colors, and multi-channel delivery.

---

## Palette config (`palette.yml`)

```yaml
global:
  primary:   "#5865F2"
  secondary: "#EB459E"
  accent:    "#FEE75C"
  success:   "#57F287"
  error:     "#ED4245"
  warning:   "#FEE75C"
  muted:     "#B0B0B0"

gradients:
  rainbow:
    stops: ["#5865F2", "#EB459E"]
  fire:
    stops: ["#FF4500", "#FF8C00", "#FFD700"]

themes:
  vip:
    primary: "#FFD700"
    accent:  "#FFA500"

themeGradients:
  vip:
    rainbow:
      stops: ["#FFD700", "#FFA500"]
```

---

## Message templates (`messages.yml`)

```yaml
messages:
  join:          "<primary>Welcome, <accent>{player_name}</accent>!</primary>"
  no_permission: "<error>✗ You don't have permission.</error>"
  reward:        "<success>✓ You received <accent>{coins}</accent> coins!</success>"
  announcement:  "<rainbow>Server event starting soon!</rainbow>"
```

---

## Setup

```java
Zelda.builder()
    .withMessaging()
    .initialize(adapter);

MessagingModule msg = Zelda.modules().find(MessagingModule.class).orElseThrow();
```

---

## Sending messages

```java
ITarget target = PlayerTarget.of(player);
ITarget console = ConsoleTarget.of(Bukkit.getConsoleSender());
ITarget sender = CommandSenderTarget.of(commandSender);
ITarget proxy = VelocityPlayerTarget.of(velocityPlayer);

// Raw string
msg.send(target, "<primary>Hello!</primary>");

// With placeholders
msg.send(target, "<primary>Hello <accent>{name}</accent>!",
    Map.of("name", player.getName()));

// Named template
msg.sendTemplate(target, "join", Map.of("player_name", player.getName()));

// Broadcast (Paper only)
msg.broadcast("<rainbow>Server restarting in 5 minutes!</rainbow>");
```

---

## Other channels

```java
// Action bar
msg.sendActionBar(target, "<accent>Coins: {coins}</accent>",
    Map.of("coins", String.valueOf(coins)));

// Title
msg.sendTitle(target, TitleData.of(
    "<primary>Round Start!</primary>",
    "<muted>Get ready...</muted>"
));

// Boss bar
msg.showBossBar(target, BossBarData.of(
    "<primary>Event in progress</primary>", 1.0f,
    BossBar.Color.BLUE, BossBar.Overlay.PROGRESS, Duration.ofSeconds(30)
));

// Sound
msg.playSound(target, SoundData.of("entity.experience_orb.pickup"));
```

---

## Per-player themes

```java
// Hook from zelda-player (no direct import needed)
msg.setThemeProvider(uuid -> playerService.getTheme(uuid));
// VIP players now get gold <primary> instead of blue
```

---

## Programmatic templates

```java
// Override a YAML template
msg.getTemplateRegistry().register("join",
    "<rainbow>Welcome {player_name}!</rainbow>");

// Add a new template
msg.getTemplateRegistry().register("vip_join",
    "<accent>Welcome back, VIP <primary>{player_name}</primary>!</accent>");
```

---

## Format only (returns Component)

```java
Component c = msg.format(target, "<primary>Hello!</primary>");
Component g = msg.formatGlobal("<error>Server error</error>");

// With Nexo or other custom tag resolvers
Component n = msg.formatWithResolvers(target, "<nexo:icon> Buy",
    Map.of(), nexoTagResolver);
```

---

## Dependencies

- `zelda-core`
- `zelda-configuration`
- `io.papermc.paper:paper-api` (optional, for Paper targets)
- `com.velocitypowered:velocity-api` (optional, for Velocity targets)