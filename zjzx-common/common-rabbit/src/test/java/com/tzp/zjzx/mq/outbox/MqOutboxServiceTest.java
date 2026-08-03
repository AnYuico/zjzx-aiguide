package com.tzp.zjzx.mq.outbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzp.zjzx.model.event.order.OrderTimeoutEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqOutboxServiceTest {

    @Mock
    private MqOutboxRepository repository;

    @Test
    void enqueueStoresProducerPayloadAndAbsoluteDeliveryTime() {
        MqOutboxService service = new MqOutboxService(
                repository, new ObjectMapper(), "service-order");
        Date deliverAt = new Date(System.currentTimeMillis() + 60000L);
        OrderTimeoutEvent event = new OrderTimeoutEvent(
                "order.timeout:order-1", "order-1", deliverAt);
        when(repository.insert(any())).thenReturn(1);

        boolean inserted = service.enqueue(event.getEventId(), "ORDER_TIMEOUT",
                "zjzx.order.events", "order.timeout.delay", event, deliverAt);

        ArgumentCaptor<MqOutboxRecord> captor = ArgumentCaptor.forClass(MqOutboxRecord.class);
        verify(repository).insert(captor.capture());
        assertTrue(inserted);
        assertEquals("service-order", captor.getValue().getProducer());
        assertEquals(deliverAt, captor.getValue().getDeliverAt());
        assertTrue(captor.getValue().getPayload().contains("order-1"));
    }
}
