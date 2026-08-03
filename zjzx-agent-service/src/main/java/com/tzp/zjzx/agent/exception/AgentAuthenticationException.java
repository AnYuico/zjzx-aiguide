package com.tzp.zjzx.agent.exception;

public class AgentAuthenticationException extends RuntimeException {

    public AgentAuthenticationException() {
        super("Mall login is required");
    }
}
