package com.tzp.zjzx.order.exception;

public class AgentOrderActionException extends RuntimeException {

    private final Reason reason;

    public AgentOrderActionException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public Reason getReason() {
        return reason;
    }

    public enum Reason {
        INVALID_REQUEST,
        NOT_FOUND,
        CONFLICT
    }
}
