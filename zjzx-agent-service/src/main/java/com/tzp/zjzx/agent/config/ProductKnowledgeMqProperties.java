package com.tzp.zjzx.agent.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "zjzx.agent.product-index-mq")
public class ProductKnowledgeMqProperties {

    private boolean enabled;
    private boolean reconciliationEnabled;
    private Duration reconciliationTimeout = Duration.ofMinutes(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isReconciliationEnabled() {
        return reconciliationEnabled;
    }

    public void setReconciliationEnabled(boolean reconciliationEnabled) {
        this.reconciliationEnabled = reconciliationEnabled;
    }

    public Duration getReconciliationTimeout() {
        return reconciliationTimeout;
    }

    public void setReconciliationTimeout(Duration reconciliationTimeout) {
        this.reconciliationTimeout = reconciliationTimeout;
    }
}
