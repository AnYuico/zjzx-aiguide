package com.tzp.zjzx.agent.exception;

public class AgentActionExpiredException extends RuntimeException {

    public AgentActionExpiredException() {
        super("Confirmation has expired");
    }
}
