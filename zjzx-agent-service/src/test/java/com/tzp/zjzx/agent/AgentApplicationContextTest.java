package com.tzp.zjzx.agent;

import com.tzp.zjzx.agent.client.ProductGuideCatalogClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AgentApplicationContextTest {

    @Autowired
    private ProductGuideCatalogClient productGuideCatalogClient;

    @Test
    void startsCompleteApplicationContext() {
        assertNotNull(productGuideCatalogClient);
    }
}
