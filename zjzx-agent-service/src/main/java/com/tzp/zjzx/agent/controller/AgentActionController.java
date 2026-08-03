package com.tzp.zjzx.agent.controller;

import com.tzp.zjzx.agent.client.PersonalDataClient;
import com.tzp.zjzx.agent.exception.AgentAuthenticationException;
import com.tzp.zjzx.agent.service.AgentActionService;
import com.tzp.zjzx.ai.contract.vo.AgentActionConfirmationVo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/agent/auth/actions")
public class AgentActionController {

    private final PersonalDataClient personalDataClient;
    private final AgentActionService actionService;

    public AgentActionController(
            PersonalDataClient personalDataClient,
            AgentActionService actionService) {
        this.personalDataClient = personalDataClient;
        this.actionService = actionService;
    }

    @PostMapping("/{confirmationId}/confirm")
    public Mono<AgentActionConfirmationVo> confirm(
            @RequestHeader(value = "token", required = false) String mallToken,
            @PathVariable String confirmationId,
            @Valid @RequestBody ConfirmationRequest request) {
        return personalDataClient.resolvePrincipal(mallToken)
                .switchIfEmpty(Mono.error(new AgentAuthenticationException()))
                .flatMap(principal -> actionService.confirm(
                        principal.getUserId(),
                        confirmationId,
                        request.confirmed()
                ));
    }

    public record ConfirmationRequest(
            @NotNull(message = "confirmed 不能为空")
            Boolean confirmed) {
    }
}
