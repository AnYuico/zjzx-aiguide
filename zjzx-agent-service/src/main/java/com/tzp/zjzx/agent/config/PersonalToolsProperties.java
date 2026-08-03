package com.tzp.zjzx.agent.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.time.Duration;

@ConfigurationProperties(prefix = "zjzx.agent.personal-tools")
public class PersonalToolsProperties implements InitializingBean {

    private boolean enabled;
    private String userServiceBaseUrl = "http://127.0.0.1:8512";
    private String cartServiceBaseUrl = "http://127.0.0.1:8513";
    private String orderServiceBaseUrl = "http://127.0.0.1:8514";
    private String internalToken;
    private Duration requestTimeout = Duration.ofSeconds(2);
    private int maxOrderLimit = 10;
    private boolean actionsEnabled;
    private Duration confirmationTtl = Duration.ofMinutes(5);
    private Duration executionLease = Duration.ofSeconds(30);
    private int maxCartQuantity = 10;

    @Override
    public void afterPropertiesSet() {
        if (actionsEnabled && !enabled) {
            throw new IllegalStateException(
                    "Personal tools must be enabled before actions"
            );
        }
        if (!enabled) {
            return;
        }
        requireText(userServiceBaseUrl, "User service base URL");
        requireText(cartServiceBaseUrl, "Cart service base URL");
        requireText(orderServiceBaseUrl, "Order service base URL");
        requireText(internalToken, "Internal API token");
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalStateException("Personal tool request timeout must be positive");
        }
        if (maxOrderLimit < 1 || maxOrderLimit > 10) {
            throw new IllegalStateException("Personal tool order limit must be between 1 and 10");
        }
        if (actionsEnabled) {
            requirePositive(confirmationTtl, "Action confirmation TTL");
            requirePositive(executionLease, "Action execution lease");
            if (maxCartQuantity < 1 || maxCartQuantity > 99) {
                throw new IllegalStateException(
                        "Action cart quantity limit must be between 1 and 99"
                );
            }
        }
    }

    private void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalStateException(name + " must be positive");
        }
    }

    private void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required when personal tools are enabled");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getUserServiceBaseUrl() {
        return userServiceBaseUrl;
    }

    public void setUserServiceBaseUrl(String userServiceBaseUrl) {
        this.userServiceBaseUrl = userServiceBaseUrl;
    }

    public String getCartServiceBaseUrl() {
        return cartServiceBaseUrl;
    }

    public void setCartServiceBaseUrl(String cartServiceBaseUrl) {
        this.cartServiceBaseUrl = cartServiceBaseUrl;
    }

    public String getOrderServiceBaseUrl() {
        return orderServiceBaseUrl;
    }

    public void setOrderServiceBaseUrl(String orderServiceBaseUrl) {
        this.orderServiceBaseUrl = orderServiceBaseUrl;
    }

    public String getInternalToken() {
        return internalToken;
    }

    public void setInternalToken(String internalToken) {
        this.internalToken = internalToken;
    }

    public Duration getRequestTimeout() {
        return requestTimeout;
    }

    public void setRequestTimeout(Duration requestTimeout) {
        this.requestTimeout = requestTimeout;
    }

    public int getMaxOrderLimit() {
        return maxOrderLimit;
    }

    public void setMaxOrderLimit(int maxOrderLimit) {
        this.maxOrderLimit = maxOrderLimit;
    }

    public boolean isActionsEnabled() {
        return actionsEnabled;
    }

    public void setActionsEnabled(boolean actionsEnabled) {
        this.actionsEnabled = actionsEnabled;
    }

    public Duration getConfirmationTtl() {
        return confirmationTtl;
    }

    public void setConfirmationTtl(Duration confirmationTtl) {
        this.confirmationTtl = confirmationTtl;
    }

    public Duration getExecutionLease() {
        return executionLease;
    }

    public void setExecutionLease(Duration executionLease) {
        this.executionLease = executionLease;
    }

    public int getMaxCartQuantity() {
        return maxCartQuantity;
    }

    public void setMaxCartQuantity(int maxCartQuantity) {
        this.maxCartQuantity = maxCartQuantity;
    }
}
