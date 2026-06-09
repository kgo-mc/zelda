package net.kgomc.zelda.bus.module;

import net.kgomc.zelda.bus.config.BusConfig;
import net.kgomc.zelda.bus.events.ZeldaEventBus;
import net.kgomc.zelda.bus.rpc.ZeldaRpc;
import net.kgomc.zelda.bus.transport.NatsTransport;
import net.kgomc.zelda.core.context.ZeldaContext;
import net.kgomc.zelda.core.lifecycle.LifecycleHook;
import net.kgomc.zelda.core.module.ZeldaModule;

import java.nio.file.Path;

/**
 * Zelda bus module — connects to NATS and exposes the type-safe
 * {@link ZeldaEventBus} and {@link ZeldaRpc}.
 *
 * <h2>Setup</h2>
 * <pre>{@code
 * Zelda.builder()
 *     .withBus()                         // reads zelda-bus.yml from central config
 *     .withBus(customConfigPath)         // explicit path
 *     .initialize(adapter);
 *
 * BusModule bus = Zelda.modules().find(BusModule.class).orElseThrow();
 * ZeldaEventBus events = bus.getEventBus();
 * ZeldaRpc       rpc   = bus.getRpc();
 * }</pre>
 *
 * <h2>Events</h2>
 * <pre>{@code
 * // Publish
 * events.publish(EconomyEvents.COINS_UPDATED, new CoinsUpdatedEvent(uuid, 0, 100, "reward"));
 * events.publish(DungeonEvents.INSTANCE_READY, payload).to("dungeon-3");
 *
 * // Subscribe
 * events.on(EconomyEvents.COINS_UPDATED)
 *       .map(EventMessage::payload)
 *       .observeOn(ZeldaSchedulers.serverThread())
 *       .subscribe(e -> scoreboard.update(e.uuid(), e.newCoins()));
 * }</pre>
 *
 * <h2>RPC</h2>
 * <pre>{@code
 * // Call (load-balanced)
 * rpc.call(EconomyRpc.GET_COINS, new GetCoinsRequest(uuid))
 *    .subscribe(resp -> player.sendMessage("Coins: " + resp.coins()));
 *
 * // Call (targeted)
 * rpc.call(EconomyRpc.GET_COINS, new GetCoinsRequest(uuid))
 *    .on("lobby-1")
 *    .subscribe(resp -> { ... });
 *
 * // Handle
 * rpc.handle(EconomyRpc.GET_COINS, req ->
 *     Single.fromCallable(() -> new GetCoinsResponse(req.uuid(), economy.getCoins(req.uuid())))
 * );
 * }</pre>
 */
public final class BusModule implements ZeldaModule, LifecycleHook {

    private final Path configPath;

    private NatsTransport transport;
    private ZeldaEventBus eventBus;
    private ZeldaRpc      rpc;

    public BusModule(Path configPath) {
        this.configPath = configPath;
    }

    // -----------------------------------------------------------------------
    // ZeldaModule
    // -----------------------------------------------------------------------

    @Override
    public String getName() {
        return "bus";
    }

    @Override
    public void onEnable(ZeldaContext context) {
        context.getLogger().info("[Zelda/Bus] Loading config from: " + configPath);

        BusConfig config = loadConfig();

        context.getLogger().info("[Zelda/Bus] Connecting to NATS: " + config.nats.url
                + " (server-id: " + config.serverId + ")");

        transport = new NatsTransport(config.nats, context.getLogger());

        try {
            transport.connect();
        } catch (Exception e) {
            throw new RuntimeException("[Zelda/Bus] Failed to connect to NATS: " + config.nats.url, e);
        }

        eventBus = new ZeldaEventBus(transport, config.serverId, context.getLogger());
        rpc      = new ZeldaRpc(transport, config.serverId, context.getLogger());

        context.getLogger().info("[Zelda/Bus] Ready — server-id=" + config.serverId);
    }

    @Override
    public void onDisable() {
        // Cleanup handled in beforeDisable() via LifecycleHook
    }

    @Override
    public void beforeDisable() {
        if (rpc != null)       rpc.dispose();
        if (eventBus != null)  eventBus.dispose();
        if (transport != null && transport.isConnected()) transport.disconnect();
    }


    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /** The type-safe event bus for pub/sub across servers. */
    public ZeldaEventBus getEventBus() {
        assertEnabled();
        return eventBus;
    }

    /** The type-safe RPC client/server. */
    public ZeldaRpc getRpc() {
        assertEnabled();
        return rpc;
    }

    // -----------------------------------------------------------------------
    // Config
    // -----------------------------------------------------------------------

    private BusConfig loadConfig() {
        try {
            return BusConfig.load(configPath);
        } catch (Exception e) {
            throw new RuntimeException("[Zelda/Bus] Failed to load bus config: " + configPath, e);
        }
    }

    private void assertEnabled() {
        if (transport == null)
            throw new IllegalStateException("BusModule is not yet enabled.");
    }
}