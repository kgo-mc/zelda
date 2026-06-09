package net.kgomc.zelda.bus.transport;

import io.nats.client.*;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.subjects.PublishSubject;
import net.kgomc.zelda.bus.config.BusConfig;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * NATS implementation of {@link BusTransport}.
 */
public final class NatsTransport implements BusTransport {

    private final BusConfig.NatsConfig config;
    private final Logger               logger;

    private Connection connection;

    /** Active subject subscriptions — subject → subject for cleanup */
    private final Map<String, Dispatcher> dispatchers = new ConcurrentHashMap<>();

    public NatsTransport(BusConfig.NatsConfig config, Logger logger) {
        this.config = config;
        this.logger = logger;
    }

    // -----------------------------------------------------------------------
    // Lifecycle
    // -----------------------------------------------------------------------

    @Override
    public void connect() throws IOException, InterruptedException {
        Options options = new Options.Builder()
                .server(config.url)
                .connectionTimeout(Duration.ofSeconds(config.connectionTimeoutSeconds))
                .maxReconnects(config.maxReconnects)
                .reconnectWait(Duration.ofMillis(config.reconnectWaitMs))
                .connectionListener((conn, type) -> {
                    logger.info("[Zelda/Bus] NATS connection event: " + type);
                })
                .errorListener(new ErrorListener() {
                    @Override
                    public void exceptionOccurred(Connection conn, Exception exp) {
                        logger.log(Level.SEVERE, "[Zelda/Bus] NATS error", exp);
                    }
                })
                .build();

        this.connection = Nats.connect(options);
        logger.info("[Zelda/Bus] Connected to NATS: " + config.url);
    }

    @Override
    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                logger.info("[Zelda/Bus] Disconnected from NATS.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public boolean isConnected() {
        return connection != null &&
                connection.getStatus() == Connection.Status.CONNECTED;
    }

    // -----------------------------------------------------------------------
    // Publish
    // -----------------------------------------------------------------------

    @Override
    public void publish(String subject, byte[] payload) {
        connection.publish(subject, payload);
    }

    // -----------------------------------------------------------------------
    // Subscribe
    // -----------------------------------------------------------------------

    @Override
    public Observable<TransportMessage> subscribe(String subject) {
        PublishSubject<TransportMessage> subject$ = PublishSubject.create();

        Dispatcher dispatcher = connection.createDispatcher(msg ->
                subject$.onNext(toTransportMessage(msg))
        );
        dispatcher.subscribe(subject);
        dispatchers.put(subject, dispatcher);

        return subject$.doOnDispose(() -> {
            Dispatcher d = dispatchers.remove(subject);
            if (d != null) {
                try { connection.closeDispatcher(d); }
                catch (Exception ignored) {}
            }
        });
    }

    @Override
    public Observable<TransportMessage> subscribeQueue(String subject, String queueGroup) {
        PublishSubject<TransportMessage> subject$ = PublishSubject.create();
        String key = subject + "@" + queueGroup;

        Dispatcher dispatcher = connection.createDispatcher(msg ->
                subject$.onNext(toTransportMessage(msg))
        );
        dispatcher.subscribe(subject, queueGroup);
        dispatchers.put(key, dispatcher);

        return subject$.doOnDispose(() -> {
            Dispatcher d = dispatchers.remove(key);
            if (d != null) {
                try { connection.closeDispatcher(d); }
                catch (Exception ignored) {}
            }
        });
    }

    // -----------------------------------------------------------------------
    // Request / Reply
    // -----------------------------------------------------------------------

    @Override
    public Single<TransportMessage> request(String subject, byte[] payload, Duration timeout) {
        return Single.fromCallable(() -> {
            Message reply = connection.request(subject, payload, timeout);
            if (reply == null) {
                throw new java.util.concurrent.TimeoutException(
                        "No reply received for subject: " + subject + " within " + timeout);
            }
            return toTransportMessage(reply);
        });
    }

    @Override
    public void reply(TransportMessage request, byte[] payload) {
        if (!request.hasReplyTo()) {
            throw new IllegalStateException("Cannot reply — message has no replyTo subject");
        }
        connection.publish(request.getReplyTo(), payload);
    }

    // -----------------------------------------------------------------------
    // Internals
    // -----------------------------------------------------------------------

    private static TransportMessage toTransportMessage(Message msg) {
        return new TransportMessage(
                msg.getSubject(),
                msg.getReplyTo(),
                msg.getData(),
                msg
        );
    }
}