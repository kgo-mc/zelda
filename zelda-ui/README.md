# zelda-ui

Config-driven inventory GUI module built on InvUI. Define menus in YAML with a layout string, conditional item rendering, palette-aware placeholders, animations, and pagination. **Server-only** — throws on Velocity.

---

## Menu config (`menus/shop.yml`)

```yaml
type: CHEST
title: "<primary>Item Shop</primary>"
rows: 5

layout:
  - "B B B B B B B B B"
  - "B X . . Y . . X B"
  - "B . . . . . . . B"
  - "B X . . Y . . X B"
  - "B B B B B B B B B"

items:
  B:
    material: BLACK_STAINED_GLASS_PANE
    name: " "

  X:
    material: STONE
    name: "<gray>Diamond Sword"
    lore:
      - "<muted>Cost: <accent>{shop_price}</accent>"
    showIfFirst:
      - showIf: canAfford
        overrides:
          material: DIAMOND_SWORD
          name: "<green>Diamond Sword"
          enchanted: true
      - overrides:
          material: BARRIER
          name: "<error>Cannot afford"

  Y:
    material: EMERALD
    name: "<green>Emerald"
    frames:
      - material: EMERALD
        delay: 10
      - material: LIME_STAINED_GLASS_PANE
        delay: 10
```

---

## Handler class (annotation style)

```java
@ZeldaUI("shop-menu")
public class ShopMenuHandler extends ZeldaMenuHandler {

    @ShowIf("canAfford")
    public boolean canAfford(ViewContext ctx) {
        return economy.getCoins(ctx.player()) >= 100;
    }

    @Placeholder("shop_price")
    public String shopPrice(ViewContext ctx) {
        return "100";
    }

    @OnLeftClick("X")
    public void onBuy(ClickContext ctx) {
        economy.deduct(ctx.player(), 100);
        ctx.player().sendMessage("Purchased!");
        ctx.close();
    }

    @OnClick({"PREV", "NEXT"})
    public void onNavigate(ClickContext ctx) {
        int page = ctx.state().get("page", Integer.class).orElse(0);
        ctx.state().put("page",
            ctx.slotCode().equals("NEXT") ? page + 1 : Math.max(0, page - 1));
    }
}
```

---

## Programmatic handler (fluent style)

```java
HandlerBuilder.create()
    .showIf("canAfford", ctx -> economy.getCoins(ctx.player()) >= 100)
    .placeholder("shop_price", ctx -> "100")
    .onLeftClick("X", ctx -> {
        economy.deduct(ctx.player(), 100);
        ctx.close();
    })
    .onOpen(ctx -> ctx.state().put("page", 0))
    .build();
```

---

## Registration and opening

```java
UIModule ui = Zelda.modules().find(UIModule.class).orElseThrow();

// Annotation style
ui.getRegistry().register(menuConfig, new ShopMenuHandler());

// Programmatic style
ui.getRegistry().register("shop", menuConfig, handler);

// Open for a player
ui.getRegistry().open("shop", player);

// Open with pre-populated state
UIStateBag state = new UIStateBag();
state.put("category", "weapons");
ui.getRegistry().open("shop", player, state);
```

---

## Conditions

`showIfFirst` — evaluated top to bottom, **first passing** condition wins (exclusive):
```yaml
showIfFirst:
  - showIf: isVip         # checked first
    overrides:
      material: DIAMOND
  - showIf: hasPremium    # checked if isVip failed
    overrides:
      material: GOLD_INGOT
  - overrides:             # no showIf = always matches (default fallback)
      material: STONE
```

`showIf` — **all passing** conditions have overrides merged cumulatively:
```yaml
showIf:
  - showIf: isOnline
    overrides:
      lore: ["<green>● Online"]
  - showIf: isVip
    overrides:
      enchanted: true
```

**Priority:** `showIfFirst` winner sets the variant → `showIf` decorates it.

---

## PAPI support

PlaceholderAPI is auto-detected at runtime. Handler `@Placeholder` methods always take priority over PAPI.

---

## Dependencies

- `zelda-core`
- `zelda-configuration`
- `io.papermc.paper:paper-api`
- `xyz.xenondevs.invui:invui`
- `me.clip:placeholderapi` (optional, auto-detected)