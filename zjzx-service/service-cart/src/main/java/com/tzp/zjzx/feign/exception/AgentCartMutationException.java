package com.tzp.zjzx.feign.exception;

public class AgentCartMutationException extends RuntimeException {

    private final Reason reason;

    public AgentCartMutationException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public AgentCartMutationException(
            Reason reason,
            String message,
            Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        INVALID_REQUEST,
        CONFLICT,
        UNAVAILABLE
    }
}
