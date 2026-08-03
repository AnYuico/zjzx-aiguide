package com.tzp.zjzx.agent.exception;

public class AgentActionUnavailableException extends RuntimeException {

    public AgentActionUnavailableException(String message) {
        super(message);
    }

    public AgentActionUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
