package com.tzp.zjzx.agent;

import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringBootVersion;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgentModuleBoundaryTest {

    @Test
    void runsOnSpringBoot35() {
        String version = SpringBootVersion.getVersion();

        assertNotNull(version);
        assertTrue(version.startsWith("3.5."));
    }

    @Test
    void exposesOnlyTheDedicatedAiContract() {
        assertNotNull(ProductGuideVo.class);
        assertNotOnClasspath("com.tzp.zjzx.model.entity.product.ProductSku");
        assertNotOnClasspath("com.tzp.zjzx.model.entity.order.OrderInfo");
        assertNotOnClasspath("com.tzp.zjzx.model.entity.user.UserAddress");
    }

    private void assertNotOnClasspath(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }
}
