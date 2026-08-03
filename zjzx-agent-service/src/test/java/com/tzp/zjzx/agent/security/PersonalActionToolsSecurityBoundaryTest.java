package com.tzp.zjzx.agent.security;

import com.tzp.zjzx.agent.service.PersonalActionTools;
import com.tzp.zjzx.ai.contract.vo.AgentActionPreparationVo;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class PersonalActionToolsSecurityBoundaryTest {

    private static final Set<String> FORBIDDEN_ARGUMENTS = Set.of(
            "userid",
            "token",
            "orderno",
            "address",
            "phone",
            "payment",
            "password"
    );

    @Test
    void exposesOnlyConfirmedActionPreparationsWithoutIdentityArguments() {
        Set<Method> toolMethods = Arrays.stream(
                        PersonalActionTools.class.getDeclaredMethods()
                )
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .collect(Collectors.toSet());

        assertEquals(
                Set.of("prepareAddToCart", "prepareCancelRecentOrder"),
                toolMethods.stream()
                        .map(method -> method.getAnnotation(Tool.class).name())
                        .collect(Collectors.toSet())
        );
        Method prepare = toolMethods.stream()
                .filter(method -> method.getName().equals("prepareAddToCart"))
                .findFirst()
                .orElseThrow();
        assertEquals(
                List.of(Long.class, Integer.class),
                List.of(prepare.getParameterTypes())
        );
        Method cancel = toolMethods.stream()
                .filter(method -> method.getName().equals(
                        "prepareCancelRecentOrder"
                ))
                .findFirst()
                .orElseThrow();
        assertEquals(
                List.of(Integer.class),
                List.of(cancel.getParameterTypes())
        );
        toolMethods.forEach(method ->
                Arrays.stream(method.getParameters())
                        .map(parameter -> parameter.getName()
                                .toLowerCase(Locale.ROOT))
                        .forEach(parameter -> FORBIDDEN_ARGUMENTS.forEach(
                                forbidden -> assertFalse(
                                        parameter.contains(forbidden)
                                )
                        ))
        );
    }

    @Test
    void preparationResponseDoesNotExposeIdentityOrTransactionData() {
        Arrays.stream(AgentActionPreparationVo.class.getDeclaredFields())
                .map(field -> field.getName().toLowerCase(Locale.ROOT))
                .forEach(field -> FORBIDDEN_ARGUMENTS.forEach(forbidden ->
                        assertFalse(
                                field.contains(forbidden),
                                "Preparation response exposes " + field
                        )
                ));
    }
}
