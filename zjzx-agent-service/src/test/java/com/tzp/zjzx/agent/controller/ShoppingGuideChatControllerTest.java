package com.tzp.zjzx.agent.controller;

import com.tzp.zjzx.agent.service.GuideChatResponse;
import com.tzp.zjzx.agent.service.ShoppingGuideChatService;
import com.tzp.zjzx.agent.exception.AgentAuthenticationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@WebFluxTest(ShoppingGuideChatController.class)
@Import(AgentExceptionHandler.class)
class ShoppingGuideChatControllerTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private ShoppingGuideChatService shoppingGuideChatService;

    @Test
    void exposesChatOnlyUnderGatewayProtectedAuthPath() {
        when(shoppingGuideChatService.chat(
                eq("mall-token"),
                eq("推荐一台电脑"),
                eq(5)
        ))
                .thenReturn(Mono.just(new GuideChatResponse(
                        "建议先明确预算。",
                        GuideChatResponse.GuideResponseMode.AI,
                        "deepseek-v4-flash",
                        List.of()
                )));

        webTestClient.post()
                .uri("/api/agent/auth/guide/chat")
                .header("token", "mall-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "message": "推荐一台电脑",
                          "limit": 5
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.mode").isEqualTo("AI")
                .jsonPath("$.model").isEqualTo("deepseek-v4-flash");
    }

    @Test
    void rejectsBlankChatMessage() {
        webTestClient.post()
                .uri("/api/agent/auth/guide/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "message": " ",
                          "limit": 5
                        }
                        """)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }

    @Test
    void returnsUnauthorizedWhenMallSessionCannotBeResolved() {
        when(shoppingGuideChatService.chat(
                isNull(),
                eq("查看我的购物车"),
                eq(5)
        )).thenReturn(Mono.error(new AgentAuthenticationException()));

        webTestClient.post()
                .uri("/api/agent/auth/guide/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "message": "查看我的购物车",
                          "limit": 5
                        }
                        """)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("AUTHENTICATION_REQUIRED");
    }
}
