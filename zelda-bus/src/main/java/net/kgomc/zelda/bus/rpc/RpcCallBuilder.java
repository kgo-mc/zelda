package net.kgomc.zelda.bus.rpc;

import io.reactivex.rxjava3.core.Single;
import net.kgomc.zelda.bus.transport.BusTransport;
import net.kgomc.zelda.core.reactive.ZeldaSchedulers;
import net.kgomc.zelda.core.serialization.ZeldaGson;

import java.time.Duration;
import java.util.UUID;

/**
 * Fluent RPC call builder returned by {@link ZeldaRpc#call}.
 *
 * <pre>{@code
 * // Load-balanced — any handler instance responds
 * rpc.call(EconomyRpc.GET_COINS, new GetCoinsRequest(uuid))
 *    .subscribe(resp -> { ... });
 *
 * // Targeted — only "lobby-1" handles it
 * rpc.call(EconomyRpc.GET_COINS, new GetCoinsRequest(uuid))
 *    .on("lobby-1")
 *    .subscribe(resp -> { ... });
 *
 * // Custom timeout
 * rpc.call(EconomyRpc.GET_COINS, req)
 *    .timeout(Duration.ofSeconds(10))
 *    .subscribe(resp -> { ... });
 * }</pre>
 */
public final class RpcCallBuilder<Req, Res> {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    private final ZeldaRpcDef<Req, Res> def;
    private final Req                   request;
    private final BusTransport          transport;
    private final String                callerId;

    private String   targetServerId = null;
    private Duration timeout        = DEFAULT_TIMEOUT;

    RpcCallBuilder(ZeldaRpcDef<Req, Res> def, Req request,
                   BusTransport transport, String callerId) {
        this.def       = def;
        this.request   = request;
        this.transport = transport;
        this.callerId  = callerId;
    }

    /**
     * Routes this RPC call to a specific server.
     * Without this, the call is load-balanced across all handler instances.
     */
    public RpcCallBuilder<Req, Res> on(String serverId) {
        this.targetServerId = serverId;
        return this;
    }

    /**
     * Sets a custom timeout. Default is 5 seconds.
     */
    public RpcCallBuilder<Req, Res> timeout(Duration timeout) {
        this.timeout = timeout;
        return this;
    }

    /**
     * Executes the RPC call and returns a {@link Single} that emits the response.
     * Subscribes on {@link ZeldaSchedulers#io()} by default.
     */
    public Single<Res> toSingle() {
        String subject = targetServerId != null
                ? def.getTargetedSubject(targetServerId)
                : def.getBaseSubject();

        RpcWireRequest wireRequest = new RpcWireRequest(
                UUID.randomUUID().toString(),
                callerId,
                def.getService(),
                def.getMethod(),
                ZeldaGson.get().toJson(request)
        );

        byte[] payload = ZeldaGson.get().toJson(wireRequest)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);

        return transport.request(subject, payload, timeout)
                .map(msg -> {
                    RpcWireResponse wireResponse = ZeldaGson.get()
                            .fromJson(msg.getDataAsString(), RpcWireResponse.class);

                    if (wireResponse.error() != null) {
                        throw new RpcException(def + " failed: " + wireResponse.error());
                    }

                    return ZeldaGson.get().fromJson(wireResponse.payload(), def.getResponseType());
                })
                .subscribeOn(ZeldaSchedulers.io());
    }

    /**
     * Blocking convenience wrapper — blocks the calling thread until the response arrives.
     * Only use on IO threads (never on the server main thread).
     */
    public Res toSync() {
        return toSingle().blockingGet();
    }

    // -----------------------------------------------------------------------
    // Wire format
    // -----------------------------------------------------------------------

    record RpcWireRequest(
            String correlationId,
            String callerId,
            String service,
            String method,
            String payload
    ) {}

    record RpcWireResponse(
            String correlationId,
            String payload,
            String error        // non-null if handler threw
    ) {}
}