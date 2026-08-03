package com.tzp.zjzx.manager.service;

import com.tzp.zjzx.ai.contract.event.ProductKnowledgeChangedEvent;
import com.tzp.zjzx.ai.contract.mq.ProductKnowledgeMqConstants;
import com.tzp.zjzx.mq.outbox.MqOutboxService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductKnowledgeOutboxServiceTest {

    @Test
    void createsUniqueProductKnowledgeEvent() {
        MqOutboxService outboxService = mock(MqOutboxService.class);
        when(outboxService.enqueue(
                anyString(),
                anyString(),
                anyString(),
                anyString(),
                any()
        )).thenReturn(true);
        ProductKnowledgeOutboxService service =
                new ProductKnowledgeOutboxService(outboxService);

        service.enqueue(10L, ProductKnowledgeOutboxService.UPDATED);

        ArgumentCaptor<String> eventId = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<ProductKnowledgeChangedEvent> event =
                ArgumentCaptor.forClass(ProductKnowledgeChangedEvent.class);
        verify(outboxService).enqueue(
                eventId.capture(),
                org.mockito.ArgumentMatchers.eq(
                        ProductKnowledgeMqConstants.CHANGED_EVENT_TYPE
                ),
                org.mockito.ArgumentMatchers.eq(
                        ProductKnowledgeMqConstants.EVENT_EXCHANGE
                ),
                org.mockito.ArgumentMatchers.eq(
                        ProductKnowledgeMqConstants.CHANGED_ROUTING_KEY
                ),
                event.capture()
        );

        assertTrue(eventId.getValue().startsWith(
                "product.knowledge.changed:10:"
        ));
        assertEquals(eventId.getValue(), event.getValue().getEventId());
        assertEquals(10L, event.getValue().getProductId());
        assertEquals(
                ProductKnowledgeOutboxService.UPDATED,
                event.getValue().getReason()
        );
        assertNotNull(event.getValue().getChangedAt());
    }

    @Test
    void rejectsInvalidProductId() {
        ProductKnowledgeOutboxService service =
                new ProductKnowledgeOutboxService(mock(MqOutboxService.class));

        assertThrows(
                IllegalArgumentException.class,
                () -> service.enqueue(0L, ProductKnowledgeOutboxService.UPDATED)
        );
    }
}
