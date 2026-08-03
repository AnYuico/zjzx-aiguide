package com.tzp.zjzx.agent.mcp;

import org.junit.jupiter.api.Test;
import org.springaicommunity.mcp.annotation.McpTool;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ProductCatalogMcpSecurityBoundaryTest {

    private static final Set<String> EXPECTED_TOOLS = Set.of(
            "searchProducts",
            "getProductSnapshot",
            "retrieveProductKnowledge"
    );
    private static final Set<String> FORBIDDEN_ARGUMENTS = Set.of(
            "userid",
            "orderno",
            "addressid",
            "paymentid",
            "token"
    );

    @Test
    void exposesExactlyTheApprovedReadOnlyTools() {
        Set<String> toolNames = Arrays.stream(
                        ProductCatalogMcpTools.class.getDeclaredMethods()
                )
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .map(method -> method.getAnnotation(McpTool.class).name())
                .collect(Collectors.toSet());

        assertEquals(EXPECTED_TOOLS, toolNames);
    }

    @Test
    void toolArgumentsCannotSelectUserOrTransactionOwnership() {
        Arrays.stream(ProductCatalogMcpTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(McpTool.class))
                .map(Method::getParameters)
                .flatMap(Arrays::stream)
                .map(Parameter::getName)
                .map(name -> name.toLowerCase(Locale.ROOT))
                .forEach(name -> assertFalse(
                        FORBIDDEN_ARGUMENTS.contains(name),
                        () -> "Forbidden MCP argument exposed: " + name
                ));
    }
}
