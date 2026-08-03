package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.ai.contract.vo.AgentActionPreparationVo;
import com.tzp.zjzx.ai.contract.vo.AgentUserPrincipalVo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonalActionToolsTest {

    @Test
    void cancellationUsesCapturedPrincipalAndOnlyAcceptsRecentPosition() {
        AgentActionService actionService = mock(AgentActionService.class);
        AgentUserPrincipalVo principal = new AgentUserPrincipalVo();
        principal.setUserId(33L);
        AgentActionPreparationVo expected = new AgentActionPreparationVo();
        expected.setConfirmationId(
                "d0b2abec-b950-4a6f-94f6-8f54647d2db6"
        );
        when(actionService.prepareCancelRecentOrder(33L, 1))
                .thenReturn(expected);
        PersonalActionTools tools =
                new PersonalActionTools(actionService, principal);

        AgentActionPreparationVo actual =
                tools.prepareCancelRecentOrder(1);

        assertEquals(expected, actual);
        assertEquals(java.util.List.of(expected), tools.preparedActions());
        verify(actionService).prepareCancelRecentOrder(33L, 1);
    }
}
