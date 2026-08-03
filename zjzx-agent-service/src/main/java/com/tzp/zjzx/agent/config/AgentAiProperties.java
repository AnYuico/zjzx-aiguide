package com.tzp.zjzx.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "zjzx.agent.ai")
public class AgentAiProperties {

    private String modelName = "deepseek-v4-flash";
    private Duration responseTimeout = Duration.ofSeconds(30);
    private Duration toolTimeout = Duration.ofSeconds(5);
    private int fallbackLimit = 5;

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Duration getResponseTimeout() {
        return responseTimeout;
    }

    public void setResponseTimeout(Duration responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public Duration getToolTimeout() {
        return toolTimeout;
    }

    public void setToolTimeout(Duration toolTimeout) {
        this.toolTimeout = toolTimeout;
    }

    public int getFallbackLimit() {
        return fallbackLimit;
    }

    public void setFallbackLimit(int fallbackLimit) {
        this.fallbackLimit = fallbackLimit;
    }
}
