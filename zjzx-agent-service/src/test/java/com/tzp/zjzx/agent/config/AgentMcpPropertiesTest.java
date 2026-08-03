package com.tzp.zjzx.agent.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentMcpPropertiesTest {

    @Test
    void allowsMissingKeyWhileMcpIsDisabled() {
        AgentMcpProperties properties = new AgentMcpProperties();

        assertDoesNotThrow(properties::afterPropertiesSet);
    }

    @Test
    void rejectsEnabledMcpWithoutApiKey() {
        AgentMcpProperties properties = new AgentMcpProperties();
        properties.setEnabled(true);

        assertThrows(
                IllegalStateException.class,
                properties::afterPropertiesSet
        );
    }

    @Test
    void rejectsShortApiKey() {
        AgentMcpProperties properties = new AgentMcpProperties();
        properties.setEnabled(true);
        properties.setApiKey("short-key");

        assertThrows(
                IllegalStateException.class,
                properties::afterPropertiesSet
        );
    }

    @Test
    void acceptsEnabledMcpWithAbsoluteEndpointAndApiKey() {
        AgentMcpProperties properties = new AgentMcpProperties();
        properties.setEnabled(true);
        properties.setEndpoint("/mcp");
        properties.setApiKey("test-key-with-at-least-32-characters");

        assertDoesNotThrow(properties::afterPropertiesSet);
    }
}
