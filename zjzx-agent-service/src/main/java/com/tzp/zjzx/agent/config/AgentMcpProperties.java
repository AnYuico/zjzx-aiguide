package com.tzp.zjzx.agent.config;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "zjzx.agent.mcp")
public class AgentMcpProperties implements InitializingBean {

    private boolean enabled;
    private String endpoint = "/mcp";
    private String apiKey;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    @Override
    public void afterPropertiesSet() {
        if (!enabled) {
            return;
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException(
                    "AGENT_MCP_API_KEY must be configured when MCP is enabled"
            );
        }
        if (apiKey.length() < 32) {
            throw new IllegalStateException(
                    "AGENT_MCP_API_KEY must contain at least 32 characters"
            );
        }
        if (!StringUtils.hasText(endpoint) || !endpoint.startsWith("/")) {
            throw new IllegalStateException(
                    "AGENT_MCP_ENDPOINT must be an absolute HTTP path"
            );
        }
    }
}
