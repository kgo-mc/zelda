package net.kgomc.zelda.bus.rpc;

import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import net.kgomc.zelda.bus.transport.BusTransport;
import net.kgomc.zelda.core.reactive.ZeldaSchedulers;
import net.kgomc.zelda.core.serialization.ZeldaGson;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Type-safe RPC over NATS.
 *
 * <h2>Calling</h2>
 * <pre>{@code
 * // Load-balanced
 * rpc.call(EconomyRpc.GET_COINS, new GetCoinsRequest(uuid))
 *    .subscribe(resp -> player.sendMessage("Coins: " + resp.coins()));
 *
 * // Targeted
 * rpc.call(EconomyRpc.GET_COINS, new GetCoinsRequest(uuid))
 *    .on("lobby-1")
 *    .subscribe(resp -> { ... });
 *
 * // Synchronous (IO thread only)
 * GetCoinsResponse resp = rpc.call(EconomyRpc.GET_COINS, req).toSync();
 * }</pre>
 *
 * <h2>Handling</h2>
 * <pre>{@code
 * rpc.handle(EconomyRpc.GET_COINS, req ->
 *     Single.fromCallable(() ->
 *         new GetCoinsResponse(req.uuid(), economy.getCoins(req.uuid()))
 *     )
 * );
 * }</pre>
 */
public final class ZeldaRpc {

    private final BusTransport transport;
    private final String       serverId;
    private final Logger       logger;

    private final CompositeDisposable disposables = new CompositeDisposable();

    public ZeldaRpc(BusTransport transport, String serverId, Logger logger) {
        this.transport = transport;
        this.serverId  = serverId;
        this.logger    = logger;
    }

    // -----------------------------------------------------------------------
    // Calling
    // -----------------------------------------------------------------------

    /**
     * Creates an RPC call builder. Chain {@code .on("serverId")} for targeted routing,
     * or call {@code .toSingle()} / {@code .toSync()} to execute.
     */
    public <Req, Res> RpcCallBuilder<Req, Res> call(ZeldaRpcDef<Req, Res> def, Req request) {
        return new RpcCallBuilder<>(def, request, transport, serverId);
    }

    // -----------------------------------------------------------------------
    // Handling
    // -----------------------------------------------------------------------

    /**
     * Registers a handler for the given RPC definition.
     *
     * <p>Uses a NATS queue group — if multiple servers register the same handler,
     * exactly ONE receives each request (load-balanced automatically).</p>
     *
     * <p>The handler also subscribes to the targeted subject
     * ({@code rpc.service.method.serverId}) so direct-routed calls work too.</p>
     *
     * @param def     the RPC definition to handle
     * @param handler a function that takes the typed request and returns Single<Response>
     */
    public <Req, Res> void handle(ZeldaRpcDef<Req, Res> def,
                                  java.util.function.Function<Req, Single<Res>> handler) {
        // Queue group subscription — load-balanced across all handler instances
        var queueSub = transport
                .subscribeQueue(def.getBaseSubject(), def.getQueueGroup())
                .subscribeOn(ZeldaSchedulers.io())
                .subscribe(
                        msg -> processRequest(msg, def, handler),
                        err -> logger.log(Level.SEVERE, "[Zelda/RPC] Handler error on " + def, err)
                );

        // Targeted subscription — handles calls routed to this specific server
        var targetedSub = transport
                .subscribe(def.getTargetedSubject(serverId))
                .subscribeOn(ZeldaSchedulers.io())
                .subscribe(
                        msg -> processRequest(msg, def, handler),
                        err -> logger.log(Level.SEVERE, "[Zelda/RPC] Targeted handler error on " + def, err)
                );

        disposables.addAll(queueSub, targetedSub);
        logger.info("[Zelda/RPC] Registered handler for: " + def);
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    public void dispose() {
        disposables.dispose();
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private <Req, Res> void processRequest(
            net.kgomc.zelda.bus.transport.TransportMessage msg,
            ZeldaRpcDef<Req, Res> def,
            java.util.function.Function<Req, Single<Res>> handler
    ) {
        RpcCallBuilder.RpcWireRequest wireRequest = ZeldaGson.get()
                .fromJson(msg.getDataAsString(), RpcCallBuilder.RpcWireRequest.class);

        Req request = ZeldaGson.get().fromJson(wireRequest.payload(), def.getRequestType());

        // Each request gets its own disposable — added to the composite so it's
        // cleaned up on shutdown. Once the Single terminates (success or error)
        // RxJava automatically removes it from the composite via dispose().
        Disposable d = handler.apply(request)
                .subscribeOn(ZeldaSchedulers.io())
                .subscribe(
                        response -> {
                            RpcCallBuilder.RpcWireResponse wireResponse =
                                    new RpcCallBuilder.RpcWireResponse(
                                            wireRequest.correlationId(),
                                            ZeldaGson.get().toJson(response),
                                            null
                                    );
                            transport.reply(msg,
                                    ZeldaGson.get().toJson(wireResponse)
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        },
                        error -> {
                            RpcCallBuilder.RpcWireResponse wireResponse =
                                    new RpcCallBuilder.RpcWireResponse(
                                            wireRequest.correlationId(),
                                            null,
                                            error.getMessage()
                                    );
                            transport.reply(msg,
                                    ZeldaGson.get().toJson(wireResponse)
                                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                            logger.warning("[Zelda/RPC] Handler threw for " + def
                                    + ": " + error.getMessage());
                        }
                );
        disposables.add(d);
    }
}