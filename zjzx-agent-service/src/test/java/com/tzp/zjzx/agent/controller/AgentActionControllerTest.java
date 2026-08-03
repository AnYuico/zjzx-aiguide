package com.tzp.zjzx.agent.controller;

import com.tzp.zjzx.agent.client.PersonalDataClient;
import com.tzp.zjzx.agent.exception.AgentActionNotFoundException;
import com.tzp.zjzx.agent.service.AgentActionService;
import com.tzp.zjzx.ai.contract.vo.AgentActionConfirmationVo;
import com.tzp.zjzx.ai.contract.vo.AgentUserPrincipalVo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.WebFluxTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.Mockito.when;

@WebFluxTest(AgentActionController.class)
@Import(AgentExceptionHandler.class)
class AgentActionControllerTest {

    private static final String CONFIRMATION_ID =
            "d0b2abec-b950-4a6f-94f6-8f54647d2db6";

    @Autowired
    private WebTestClient webTestClient;

    @MockitoBean
    private PersonalDataClient personalDataClient;

    @MockitoBean
    private AgentActionService actionService;

    @Test
    void resolvesUserFromTokenAndConfirmsServerStoredAction() {
        AgentUserPrincipalVo principal = new AgentUserPrincipalVo();
        principal.setUserId(33L);
        AgentActionConfirmationVo result = new AgentActionConfirmationVo();
        result.setConfirmationId(CONFIRMATION_ID);
        result.setStatus("SUCCEEDED");
        result.setReplayed(false);
        when(personalDataClient.resolvePrincipal("mall-token"))
                .thenReturn(Mono.just(principal));
        when(actionService.confirm(33L, CONFIRMATION_ID, true))
                .thenReturn(Mono.just(result));

        webTestClient.post()
                .uri("/api/agent/auth/actions/"
                        + CONFIRMATION_ID + "/confirm")
                .header("token", "mall-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "confirmed": true
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.confirmationId").isEqualTo(CONFIRMATION_ID)
                .jsonPath("$.status").isEqualTo("SUCCEEDED")
                .jsonPath("$.replayed").isEqualTo(false);
    }

    @Test
    void hidesActionsOwnedByAnotherUser() {
        AgentUserPrincipalVo principal = new AgentUserPrincipalVo();
        principal.setUserId(34L);
        when(personalDataClient.resolvePrincipal("other-user-token"))
                .thenReturn(Mono.just(principal));
        when(actionService.confirm(34L, CONFIRMATION_ID, true))
                .thenReturn(Mono.error(new AgentActionNotFoundException()));

        webTestClient.post()
                .uri("/api/agent/auth/actions/"
                        + CONFIRMATION_ID + "/confirm")
                .header("token", "other-user-token")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "confirmed": true
                        }
                        """)
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.code").isEqualTo("ACTION_NOT_FOUND");
    }

    @Test
    void rejectsMissingConfirmationDecision() {
        webTestClient.post()
                .uri("/api/agent/auth/actions/"
                        + CONFIRMATION_ID + "/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{}")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.code").isEqualTo("INVALID_REQUEST");
    }
}
