package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.agent.client.PersonalDataClient;
import com.tzp.zjzx.ai.contract.vo.AgentCartItemVo;
import com.tzp.zjzx.ai.contract.vo.AgentOrderSummaryVo;
import com.tzp.zjzx.ai.contract.vo.AgentUserPrincipalVo;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonalReadToolsTest {

    private final PersonalDataClient client = mock(PersonalDataClient.class);
    private final PersonalReadTools tools = new PersonalReadTools(
            client,
            principal(33L),
            10,
            Duration.ofSeconds(1)
    );

    @Test
    void bindsCartReadToResolvedPrincipal() {
        AgentCartItemVo item = new AgentCartItemVo();
        item.setSkuId(14L);
        when(client.getCart(33L)).thenReturn(Mono.just(List.of(item)));

        List<AgentCartItemVo> result = tools.getMyCart();

        assertEquals(14L, result.get(0).getSkuId());
        verify(client).getCart(33L);
    }

    @Test
    void normalizesOrderStatusAndDefaultLimit() {
        when(client.listRecentOrders(33L, "WAITING_PAYMENT", 5))
                .thenReturn(Mono.just(List.of(new AgentOrderSummaryVo())));

        tools.listMyRecentOrders(" waiting_payment ", null);

        verify(client).listRecentOrders(33L, "WAITING_PAYMENT", 5);
    }

    @Test
    void rejectsUnknownStatusBeforeCallingOrderService() {
        assertThrows(
                IllegalArgumentException.class,
                () -> tools.listMyRecentOrders("OTHER_USER", 5)
        );
    }

    private AgentUserPrincipalVo principal(Long userId) {
        AgentUserPrincipalVo principal = new AgentUserPrincipalVo();
        principal.setUserId(userId);
        principal.setNickName("test");
        return principal;
    }
}
