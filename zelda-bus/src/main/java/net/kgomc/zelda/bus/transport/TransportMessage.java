package net.kgomc.zelda.bus.transport;

import io.nats.client.Message;

/**
 * A raw message received from the transport layer.
 * Wraps the NATS {@link Message} to keep the rest of the codebase
 * decoupled from the NATS client API.
 */
public final class TransportMessage {

    private final String subject;
    private final String replyTo;   // non-null for RPC requests
    private final byte[] data;
    private final Object nativeMessage; // raw NATS Message for reply

    public TransportMessage(String subject, String replyTo, byte[] data, Object nativeMessage) {
        this.subject       = subject;
        this.replyTo       = replyTo;
        this.data          = data;
        this.nativeMessage = nativeMessage;
    }

    public String  getSubject()       { return subject; }
    public String  getReplyTo()       { return replyTo; }
    public byte[]  getData()          { return data; }
    public boolean hasReplyTo()       { return replyTo != null && !replyTo.isEmpty(); }
    public Object  getNativeMessage() { return nativeMessage; }

    public String getDataAsString() {
        return new String(data, java.nio.charset.StandardCharsets.UTF_8);
    }
}