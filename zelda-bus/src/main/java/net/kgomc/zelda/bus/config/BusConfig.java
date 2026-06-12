package net.kgomc.zelda.bus.config;

import net.kgomc.zelda.core.serialization.ZeldaGson;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration for {@code zelda-bus.json}.
 *
 * <p>Example:</p>
 * <pre>{@code
 * {
 *   "serverId": "lobby-1",
 *   "nats": {
 *     "url": "nats://localhost:4222",
 *     "connectionTimeoutSeconds": 5,
 *     "maxReconnects": -1,
 *     "reconnectWaitMs": 1000
 *   }
 * }
 * }</pre>
 */
public final class BusConfig {

    /**
     * Unique identifier for this server on the bus.
     * Used for targeted routing: {@code rpc.call(def, req).on("lobby-1")}
     * Must be unique across all servers on the same NATS cluster.
     */
    public String serverId = "server-1";

    /** NATS connection settings. */
    public NatsConfig nats = new NatsConfig();

    public static final class NatsConfig {
        /** NATS server URL. Comma-separate for clustering: nats://host1:4222,nats://host2:4222 */
        public String url = "nats://localhost:4222";

        /** Connection timeout in seconds. */
        public int connectionTimeoutSeconds = 5;

        /** Max reconnect attempts. -1 = unlimited. */
        public int maxReconnects = -1;

        /** Reconnect wait in milliseconds. */
        public long reconnectWaitMs = 1000;
    }

    // -----------------------------------------------------------------------
    // Factory
    // -----------------------------------------------------------------------

    /**
     * Loads a {@link BusConfig} from the given JSON file.
     * Creates the file with defaults if it doesn't exist.
     */
    public static BusConfig load(Path path) throws IOException {
        if (!Files.exists(path)) {
            // Write defaults so the user has a template to edit
            Files.createDirectories(path.getParent());
            try (var writer = Files.newBufferedWriter(path)) {
                ZeldaGson.toJson(new BusConfig(), writer);
            }
            return new BusConfig();
        }

        try (Reader reader = Files.newBufferedReader(path)) {
            return ZeldaGson.fromJson(reader, BusConfig.class);
        }
    }
}