package com.tzp.zjzx.agent.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzp.zjzx.agent.client.PersonalDataClient;
import com.tzp.zjzx.agent.config.AgentAiProperties;
import com.tzp.zjzx.agent.config.PersonalToolsProperties;
import com.tzp.zjzx.agent.observability.AgentTelemetry;
import com.tzp.zjzx.agent.resilience.AgentResilienceExecutor;
import com.tzp.zjzx.agent.service.GuideChatResponse;
import com.tzp.zjzx.agent.service.GuideSearchResponse;
import com.tzp.zjzx.agent.service.ProductCatalogTools;
import com.tzp.zjzx.agent.service.ShoppingGuideChatService;
import com.tzp.zjzx.agent.service.AgentActionService;
import com.tzp.zjzx.agent.service.ShoppingGuideService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentSecurityEvaluationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GuideOutputGuard outputGuard = new GuideOutputGuard();

    @Test
    void evaluationDatasetStaysInsideReadOnlyFallbackBoundary()
            throws Exception {
        List<EvaluationCase> cases = loadCases();
        assertTrue(cases.size() >= 10);
        assertTrue(cases.stream()
                .map(EvaluationCase::category)
                .collect(java.util.stream.Collectors.toSet())
                .size() >= 7);

        ShoppingGuideService guideService = mock(ShoppingGuideService.class);
        when(guideService.search(any(), any(Integer.class)))
                .thenReturn(Mono.just(new GuideSearchResponse(
                        "evaluation",
                        "No matching catalog item",
                        0,
                        List.of()
                )));
        ShoppingGuideChatService chatService = service(guideService);

        for (EvaluationCase evaluationCase : cases) {
            StepVerifier.create(chatService.chat(
                            evaluationCase.prompt(),
                            evaluationCase.limit()
                    ))
                    .assertNext(response -> {
                        assertEquals(
                                GuideChatResponse.GuideResponseMode
                                        .DETERMINISTIC_FALLBACK,
                                response.mode(),
                                evaluationCase.id()
                        );
                        assertTrue(
                                outputGuard.isSafe(response.answer()),
                                evaluationCase.id()
                        );
                        assertTrue(
                                response.products().isEmpty(),
                                evaluationCase.id()
                        );
                    })
                    .verifyComplete();
        }
    }

    @Test
    void exposesOnlySearchProductsAsModelTool() {
        Set<String> tools = new HashSet<>();
        Arrays.stream(ProductCatalogTools.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .map(method -> method.getAnnotation(Tool.class).name())
                .forEach(tools::add);

        assertEquals(Set.of("searchProducts"), tools);
    }

    private ShoppingGuideChatService service(
            ShoppingGuideService guideService) {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);

        AgentResilienceExecutor resilience =
                mock(AgentResilienceExecutor.class);
        AgentTelemetry telemetry = mock(AgentTelemetry.class);
        when(telemetry.observeChat(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        AgentAiProperties properties = new AgentAiProperties();
        properties.setModelName("disabled");
        properties.setResponseTimeout(Duration.ofSeconds(1));
        properties.setToolTimeout(Duration.ofSeconds(1));
        properties.setFallbackLimit(5);
        return new ShoppingGuideChatService(
                provider,
                guideService,
                properties,
                resilience,
                telemetry,
                outputGuard,
                mock(PersonalDataClient.class),
                new PersonalToolsProperties(),
                mock(AgentActionService.class)
        );
    }

    private List<EvaluationCase> loadCases() throws Exception {
        ClassPathResource resource = new ClassPathResource(
                "evaluation/agent-security-eval.jsonl"
        );
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        resource.getInputStream(),
                        StandardCharsets.UTF_8
                ))) {
            return reader.lines()
                    .filter(line -> !line.isBlank())
                    .map(this::readCase)
                    .toList();
        }
    }

    private EvaluationCase readCase(String json) {
        try {
            return objectMapper.readValue(json, EvaluationCase.class);
        } catch (Exception exception) {
            throw new IllegalArgumentException(
                    "Invalid security evaluation case",
                    exception
            );
        }
    }

    private record EvaluationCase(
            String id,
            String category,
            String prompt,
            Integer limit) {
    }
}
