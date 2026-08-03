package com.tzp.zjzx.mq.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "zjzx.mq.outbox")
public class MqOutboxProperties {

    private boolean enabled;
    private int batchSize = 20;
    private int maxRetries = 20;
    private long confirmTimeoutMillis = 5000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public long getConfirmTimeoutMillis() {
        return confirmTimeoutMillis;
    }

    public void setConfirmTimeoutMillis(long confirmTimeoutMillis) {
        this.confirmTimeoutMillis = confirmTimeoutMillis;
    }
}
