package com.tzp.zjzx.agent.security;

import com.tzp.zjzx.agent.service.PersonalReadTools;
import com.tzp.zjzx.ai.contract.vo.AgentCartItemVo;
import com.tzp.zjzx.ai.contract.vo.AgentOrderSummaryVo;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PersonalReadToolsSecurityBoundaryTest {

    private static final Set<String> FORBIDDEN_NAMES = Set.of(
            "userid",
            "token",
            "orderno",
            "address",
            "phone",
            "payment",
            "password"
    );

    @Test
    void exposesExactlyTwoReadOnlyToolsWithoutIdentityArguments() {
        Set<Method> methods = Arrays.stream(PersonalReadTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .collect(Collectors.toSet());

        assertEquals(
                Set.of("getMyCart", "listMyRecentOrders"),
                methods.stream()
                        .map(method -> method.getAnnotation(Tool.class).name())
                        .collect(Collectors.toSet())
        );
        Method cart = methods.stream()
                .filter(method -> method.getName().equals("getMyCart"))
                .findFirst()
                .orElseThrow();
        Method orders = methods.stream()
                .filter(method -> method.getName().equals("listMyRecentOrders"))
                .findFirst()
                .orElseThrow();
        assertEquals(0, cart.getParameterCount());
        assertEquals(
                java.util.List.of(String.class, Integer.class),
                java.util.List.of(orders.getParameterTypes())
        );
    }

    @Test
    void personalToolVosExcludeIdentityAndTransactionSecrets() {
        assertNoForbiddenFields(AgentCartItemVo.class);
        assertNoForbiddenFields(AgentOrderSummaryVo.class);
    }

    private void assertNoForbiddenFields(Class<?> type) {
        Arrays.stream(type.getDeclaredFields())
                .map(field -> field.getName().toLowerCase(Locale.ROOT))
                .forEach(field -> FORBIDDEN_NAMES.forEach(forbidden ->
                        assertFalse(
                                field.contains(forbidden),
                                type.getSimpleName() + " exposes " + field
                        )
                ));
    }
}
