package com.tzp.zjzx.agent.exception;

public class AgentActionNotFoundException extends RuntimeException {

    public AgentActionNotFoundException() {
        super("Confirmation was not found");
    }
}
