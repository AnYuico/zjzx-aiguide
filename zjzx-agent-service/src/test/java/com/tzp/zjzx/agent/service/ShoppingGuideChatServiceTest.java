package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.agent.client.PersonalDataClient;
import com.tzp.zjzx.agent.config.AgentAiProperties;
import com.tzp.zjzx.agent.config.PersonalToolsProperties;
import com.tzp.zjzx.agent.exception.AgentAuthenticationException;
import com.tzp.zjzx.agent.exception.PersonalDataUnavailableException;
import com.tzp.zjzx.agent.observability.AgentTelemetry;
import com.tzp.zjzx.agent.resilience.AgentResilienceExecutor;
import com.tzp.zjzx.agent.security.GuideOutputGuard;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ShoppingGuideChatServiceTest {

    private final ShoppingGuideService shoppingGuideService =
            mock(ShoppingGuideService.class);
    private final PersonalDataClient personalDataClient =
            mock(PersonalDataClient.class);
    private final AgentActionService actionService =
            mock(AgentActionService.class);

    @Test
    void usesDeterministicCatalogWhenChatModelIsDisabled() {
        ProductGuideVo product = product();
        when(shoppingGuideService.search(eq("推荐一台Mac"), eq(5)))
                .thenReturn(Mono.just(new GuideSearchResponse(
                        "推荐一台Mac",
                        "已找到 1 个相关商品。",
                        1,
                        List.of(product)
                )));
        ShoppingGuideChatService service = serviceWithoutModel();

        StepVerifier.create(service.chat("推荐一台Mac", null))
                .assertNext(response -> {
                    assertEquals(GuideChatResponse.GuideResponseMode.DETERMINISTIC_FALLBACK,
                            response.mode());
                    assertNull(response.model());
                    assertEquals(14L, response.products().get(0).getSkuId());
                })
                .verifyComplete();
    }

    @Test
    void enabledPersonalToolsRejectInvalidMallSessionBeforeModelUse() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        when(personalDataClient.resolvePrincipal("expired-token"))
                .thenReturn(Mono.error(new AgentAuthenticationException()));
        PersonalToolsProperties personalProperties = new PersonalToolsProperties();
        personalProperties.setEnabled(true);
        personalProperties.setInternalToken("internal-secret");
        ShoppingGuideChatService service = new ShoppingGuideChatService(
                provider,
                shoppingGuideService,
                properties(),
                passThroughResilience(),
                passThroughTelemetry(),
                safeOutputGuard(),
                personalDataClient,
                personalProperties,
                actionService
        );

        StepVerifier.create(service.chat("expired-token", "查看我的购物车", 5))
                .expectError(AgentAuthenticationException.class)
                .verify();
    }

    @Test
    void personalDataFailureIsNotConvertedToProductFallback() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class))).thenThrow(
                new IllegalStateException(
                        new PersonalDataUnavailableException(
                                "Cart service request failed"
                        )
                )
        );
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        com.tzp.zjzx.ai.contract.vo.AgentUserPrincipalVo principal =
                new com.tzp.zjzx.ai.contract.vo.AgentUserPrincipalVo();
        principal.setUserId(33L);
        when(personalDataClient.resolvePrincipal("mall-token"))
                .thenReturn(Mono.just(principal));
        PersonalToolsProperties personalProperties = new PersonalToolsProperties();
        personalProperties.setEnabled(true);
        personalProperties.setInternalToken("internal-secret");
        ShoppingGuideChatService service = new ShoppingGuideChatService(
                provider,
                shoppingGuideService,
                properties(),
                passThroughResilience(),
                passThroughTelemetry(),
                safeOutputGuard(),
                personalDataClient,
                personalProperties,
                actionService
        );

        StepVerifier.create(service.chat("mall-token", "查看我的购物车", 5))
                .expectError(PersonalDataUnavailableException.class)
                .verify();
    }

    @Test
    void returnsAiResponseWhenDeepSeekCompletesNormally() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(
                        new Generation(new AssistantMessage("建议选择 Mac mini。"))
                )));
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        ShoppingGuideChatService service = new ShoppingGuideChatService(
                provider,
                shoppingGuideService,
                properties(),
                passThroughResilience(),
                passThroughTelemetry(),
                safeOutputGuard(),
                personalDataClient,
                disabledPersonalTools(),
                actionService
        );

        StepVerifier.create(service.chat("推荐一台Mac", 5))
                .assertNext(response -> {
                    assertEquals(GuideChatResponse.GuideResponseMode.AI, response.mode());
                    assertEquals("deepseek-v4-flash", response.model());
                    assertEquals("建议选择 Mac mini。", response.answer());
                })
                .verifyComplete();
    }

    @Test
    void replacesEmptyAiCatalogAnswerWithCurrentCatalogFallback() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(
                        new Generation(new AssistantMessage(
                                "未查询到有在售商品"
                        ))
                )));
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        when(shoppingGuideService.search(
                eq("帮我推荐一台适合办公的电脑"),
                eq(5)
        )).thenReturn(Mono.just(new GuideSearchResponse(
                "帮我推荐一台适合办公的电脑",
                "暂未找到符合条件的商品。",
                0,
                List.of()
        )));
        when(shoppingGuideService.search(isNull(), eq(5)))
                .thenReturn(Mono.just(new GuideSearchResponse(
                        null,
                        "已为你展示当前可选商品。",
                        1,
                        List.of(product())
                )));
        ShoppingGuideChatService service = new ShoppingGuideChatService(
                provider,
                shoppingGuideService,
                properties(),
                passThroughResilience(),
                passThroughTelemetry(),
                safeOutputGuard(),
                personalDataClient,
                disabledPersonalTools(),
                actionService
        );

        StepVerifier.create(service.chat(
                        "帮我推荐一台适合办公的电脑",
                        5
                ))
                .assertNext(response -> {
                    assertEquals(
                            GuideChatResponse.GuideResponseMode
                                    .DETERMINISTIC_FALLBACK,
                            response.mode()
                    );
                    assertEquals(14L, response.products().get(0).getSkuId());
                })
                .verifyComplete();
    }

    @Test
    void fallsBackWhenDeepSeekCallFails() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("provider unavailable"));
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        when(shoppingGuideService.search(eq("推荐一台Mac"), eq(5)))
                .thenReturn(Mono.just(new GuideSearchResponse(
                        "推荐一台Mac",
                        "暂未找到符合条件的商品，请尝试更换关键词。",
                        0,
                        List.of()
                )));
        when(shoppingGuideService.search(isNull(), eq(5)))
                .thenReturn(Mono.just(new GuideSearchResponse(
                        null,
                        "暂未找到在售商品。",
                        0,
                        List.of()
                )));
        ShoppingGuideChatService service = new ShoppingGuideChatService(
                provider,
                shoppingGuideService,
                properties(),
                passThroughResilience(),
                passThroughTelemetry(),
                safeOutputGuard(),
                personalDataClient,
                disabledPersonalTools(),
                actionService
        );

        StepVerifier.create(service.chat("推荐一台Mac", 5))
                .assertNext(response -> assertEquals(
                        GuideChatResponse.GuideResponseMode.DETERMINISTIC_FALLBACK,
                        response.mode()
                ))
                .verifyComplete();
    }

    @Test
    void showsCurrentCatalogWhenNaturalLanguageFallbackHasNoExactMatch() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new IllegalStateException("provider unavailable"));
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        when(shoppingGuideService.search(
                eq("帮我推荐一台适合办公的电脑"),
                eq(5)
        )).thenReturn(Mono.just(new GuideSearchResponse(
                "帮我推荐一台适合办公的电脑",
                "暂未找到符合条件的商品。",
                0,
                List.of()
        )));
        when(shoppingGuideService.search(isNull(), eq(5)))
                .thenReturn(Mono.just(new GuideSearchResponse(
                        null,
                        "已为你展示当前可选商品。",
                        1,
                        List.of(product())
                )));
        ShoppingGuideChatService service = new ShoppingGuideChatService(
                provider,
                shoppingGuideService,
                properties(),
                passThroughResilience(),
                passThroughTelemetry(),
                safeOutputGuard(),
                personalDataClient,
                disabledPersonalTools(),
                actionService
        );

        StepVerifier.create(service.chat(
                        "帮我推荐一台适合办公的电脑",
                        5
                ))
                .assertNext(response -> {
                    assertEquals(
                            GuideChatResponse.GuideResponseMode
                                    .DETERMINISTIC_FALLBACK,
                            response.mode()
                    );
                    assertEquals(14L, response.products().get(0).getSkuId());
                    org.junit.jupiter.api.Assertions.assertTrue(
                            response.answer().contains("当前在售商品")
                    );
                })
                .verifyComplete();

        verify(shoppingGuideService).search(isNull(), eq(5));
    }

    private ShoppingGuideChatService serviceWithoutModel() {
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(null);
        return new ShoppingGuideChatService(
                provider,
                shoppingGuideService,
                properties(),
                passThroughResilience(),
                passThroughTelemetry(),
                safeOutputGuard(),
                personalDataClient,
                disabledPersonalTools(),
                actionService
        );
    }

    @Test
    void rejectsUnsafeModelOutputAndFallsBackToCatalog() {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(new ChatResponse(List.of(
                        new Generation(new AssistantMessage(
                                "已为您下单，订单号 aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
                        ))
                )));
        @SuppressWarnings("unchecked")
        ObjectProvider<ChatModel> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(chatModel);
        when(shoppingGuideService.search(any(), eq(5)))
                .thenReturn(Mono.just(new GuideSearchResponse(
                        "Mac",
                        "未找到商品",
                        0,
                        List.of()
                )));
        AgentTelemetry telemetry = passThroughTelemetry();
        ShoppingGuideChatService service = new ShoppingGuideChatService(
                provider,
                shoppingGuideService,
                properties(),
                passThroughResilience(),
                telemetry,
                new GuideOutputGuard(),
                personalDataClient,
                disabledPersonalTools(),
                actionService
        );

        StepVerifier.create(service.chat("帮我推荐一台 Mac", 5))
                .assertNext(response -> assertEquals(
                        GuideChatResponse.GuideResponseMode.DETERMINISTIC_FALLBACK,
                        response.mode()
                ))
                .verifyComplete();

        verify(telemetry).recordUnsafeOutput();
    }

    private AgentResilienceExecutor passThroughResilience() {
        AgentResilienceExecutor executor = mock(AgentResilienceExecutor.class);
        when(executor.protectDeepSeek(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return executor;
    }

    private AgentTelemetry passThroughTelemetry() {
        AgentTelemetry telemetry = mock(AgentTelemetry.class);
        when(telemetry.observeChat(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return telemetry;
    }

    private GuideOutputGuard safeOutputGuard() {
        GuideOutputGuard guard = mock(GuideOutputGuard.class);
        when(guard.isSafe(any())).thenReturn(true);
        return guard;
    }

    private AgentAiProperties properties() {
        AgentAiProperties properties = new AgentAiProperties();
        properties.setModelName("deepseek-v4-flash");
        properties.setResponseTimeout(Duration.ofSeconds(2));
        properties.setToolTimeout(Duration.ofSeconds(1));
        properties.setFallbackLimit(5);
        return properties;
    }

    private PersonalToolsProperties disabledPersonalTools() {
        PersonalToolsProperties properties = new PersonalToolsProperties();
        properties.setEnabled(false);
        return properties;
    }

    private ProductGuideVo product() {
        ProductGuideVo product = new ProductGuideVo();
        product.setSkuId(14L);
        product.setProductName("Mac mini");
        product.setInStock(true);
        return product;
    }
}
