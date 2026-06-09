package net.kgomc.zelda.bus.transport;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;

import java.time.Duration;

/**
 * Low-level message transport abstraction.
 * Currently implemented by {@link NatsTransport}.
 */
public interface BusTransport {

    /** Connect to the broker. */
    void connect() throws Exception;

    /** Disconnect cleanly. */
    void disconnect();

    /** Whether the transport is currently connected. */
    boolean isConnected();

    /**
     * Publishes a raw message to a subject.
     *
     * @param subject NATS subject string
     * @param payload serialized payload bytes
     */
    void publish(String subject, byte[] payload);

    /**
     * Subscribes to a subject — emits every message received.
     * Subject supports NATS wildcards: {@code economy.>} or {@code economy.*}
     */
    Observable<TransportMessage> subscribe(String subject);

    /**
     * Subscribes to a subject as part of a queue group.
     * Only ONE subscriber in the group receives each message (load-balanced).
     * Used for RPC handlers.
     */
    Observable<TransportMessage> subscribeQueue(String subject, String queueGroup);

    /**
     * Sends a request and waits for exactly one reply.
     * Used for RPC calls.
     *
     * @param subject NATS subject
     * @param payload request payload
     * @param timeout max wait for reply
     * @return Single that emits the reply or errors on timeout
     */
    Single<TransportMessage> request(String subject, byte[] payload, Duration timeout);

    /**
     * Sends a reply to an inbox subject (used by RPC responders).
     */
    void reply(TransportMessage request, byte[] payload);
}