package com.tzp.zjzx.agent.config;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersonalToolsPropertiesTest {

    @Test
    void disabledToolsDoNotRequireSecrets() {
        PersonalToolsProperties properties = new PersonalToolsProperties();

        assertDoesNotThrow(properties::afterPropertiesSet);
    }

    @Test
    void enabledToolsRequireInternalToken() {
        PersonalToolsProperties properties = validEnabledProperties();
        properties.setInternalToken(" ");

        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
    }

    @Test
    void enabledToolsAcceptCompleteConfiguration() {
        PersonalToolsProperties properties = validEnabledProperties();

        assertDoesNotThrow(properties::afterPropertiesSet);
    }

    @Test
    void actionsCannotBeEnabledWithoutPersonalIdentityTools() {
        PersonalToolsProperties properties = new PersonalToolsProperties();
        properties.setActionsEnabled(true);

        assertThrows(IllegalStateException.class, properties::afterPropertiesSet);
    }

    @Test
    void enabledActionsAcceptBoundedConfirmationConfiguration() {
        PersonalToolsProperties properties = validEnabledProperties();
        properties.setActionsEnabled(true);
        properties.setConfirmationTtl(Duration.ofMinutes(5));
        properties.setExecutionLease(Duration.ofSeconds(30));
        properties.setMaxCartQuantity(10);

        assertDoesNotThrow(properties::afterPropertiesSet);
    }

    private PersonalToolsProperties validEnabledProperties() {
        PersonalToolsProperties properties = new PersonalToolsProperties();
        properties.setEnabled(true);
        properties.setInternalToken("internal-secret");
        properties.setRequestTimeout(Duration.ofSeconds(2));
        properties.setMaxOrderLimit(10);
        return properties;
    }
}
