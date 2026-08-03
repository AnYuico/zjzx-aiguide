package com.tzp.zjzx.agent.action;

public enum AgentActionStatus {
    PENDING,
    EXECUTING,
    SUCCEEDED,
    FAILED_RETRYABLE,
    EXPIRED,
    REJECTED
}
