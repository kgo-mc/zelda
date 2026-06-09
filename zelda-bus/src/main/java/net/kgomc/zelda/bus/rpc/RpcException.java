package net.kgomc.zelda.bus.rpc;

/**
 * Thrown when an RPC call fails — either the handler threw an exception,
 * the call timed out, or no handler was found.
 */
public class RpcException extends RuntimeException {
    public RpcException(String message) { super(message); }
    public RpcException(String message, Throwable cause) { super(message, cause); }
}