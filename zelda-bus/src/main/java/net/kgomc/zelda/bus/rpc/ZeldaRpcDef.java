package net.kgomc.zelda.bus.rpc;

/**
 * Type-safe RPC definition. Plugin authors declare these as public static
 * constants in their API artifact.
 *
 * <h2>Declaring RPC endpoints (in your plugin's API module)</h2>
 * <pre>{@code
 * public final class EconomyRpc {
 *
 *     public static final ZeldaRpcDef<GetCoinsRequest, GetCoinsResponse> GET_COINS =
 *         ZeldaRpcDef.of("economy", "getCoins",
 *             GetCoinsRequest.class, GetCoinsResponse.class);
 *
 *     public static final ZeldaRpcDef<TransferRequest, TransferResponse> TRANSFER =
 *         ZeldaRpcDef.of("economy", "transfer",
 *             TransferRequest.class, TransferResponse.class);
 * }
 * }</pre>
 *
 * <h2>Handling (in your plugin implementation)</h2>
 * <pre>{@code
 * rpc.handle(EconomyRpc.GET_COINS, req ->
 *     Single.fromCallable(() ->
 *         new GetCoinsResponse(req.uuid(), economy.getCoins(req.uuid()))
 *     )
 * );
 * }</pre>
 *
 * <h2>Calling (from any plugin that depends on the API)</h2>
 * <pre>{@code
 * // Load-balanced — any server running the handler responds
 * rpc.call(EconomyRpc.GET_COINS, new GetCoinsRequest(uuid))
 *    .subscribeOn(ZeldaSchedulers.io())
 *    .subscribe(resp -> player.sendMessage("Coins: " + resp.coins()));
 *
 * // Targeted — must run on "lobby-1"
 * rpc.call(EconomyRpc.GET_COINS, new GetCoinsRequest(uuid))
 *    .on("lobby-1")
 *    .subscribeOn(ZeldaSchedulers.io())
 *    .subscribe(resp -> { ... });
 * }</pre>
 *
 * @param <Req> request payload type
 * @param <Res> response payload type
 */
public final class ZeldaRpcDef<Req, Res> {

    private final String     service;
    private final String     method;
    private final Class<Req> requestType;
    private final Class<Res> responseType;

    /** Base NATS subject: {@code rpc.{service}.{method}} */
    private final String baseSubject;

    private ZeldaRpcDef(String service, String method,
                        Class<Req> requestType, Class<Res> responseType) {
        this.service      = service;
        this.method       = method;
        this.requestType  = requestType;
        this.responseType = responseType;
        this.baseSubject  = "rpc." + service + "." + method;
    }

    public static <Req, Res> ZeldaRpcDef<Req, Res> of(
            String service, String method,
            Class<Req> requestType, Class<Res> responseType
    ) {
        return new ZeldaRpcDef<>(service, method, requestType, responseType);
    }

    public String     getService()      { return service; }
    public String     getMethod()       { return method; }
    public Class<Req> getRequestType()  { return requestType; }
    public Class<Res> getResponseType() { return responseType; }
    public String     getBaseSubject()  { return baseSubject; }

    /**
     * Subject for targeted calls: {@code rpc.{service}.{method}.{serverId}}
     */
    public String getTargetedSubject(String serverId) {
        return baseSubject + "." + serverId;
    }

    /**
     * Queue group name — all instances of a service share this group
     * so exactly one handles each request.
     */
    public String getQueueGroup() {
        return "rpc-" + service + "-" + method;
    }

    @Override
    public String toString() {
        return "ZeldaRpcDef{" + baseSubject + "}";
    }
}