package com.tzp.zjzx.mq.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.Date;

public class MqOutboxService {

    private final MqOutboxRepository repository;
    private final ObjectMapper objectMapper;
    private final String producer;

    public MqOutboxService(MqOutboxRepository repository,
                           ObjectMapper objectMapper,
                           String producer) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.producer = producer;
    }

    public boolean enqueue(String eventId, String eventType, String exchangeName,
                           String routingKey, Object event) {
        return enqueue(eventId, eventType, exchangeName, routingKey, event, null);
    }

    public boolean enqueue(String eventId, String eventType, String exchangeName,
                           String routingKey, Object event, Date deliverAt) {
        requireText(eventId, "eventId");
        requireText(eventType, "eventType");
        requireText(exchangeName, "exchangeName");
        requireText(routingKey, "routingKey");
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }

        MqOutboxRecord record = new MqOutboxRecord();
        record.setEventId(eventId);
        record.setProducer(producer);
        record.setEventType(eventType);
        record.setExchangeName(exchangeName);
        record.setRoutingKey(routingKey);
        record.setPayload(toJson(event));
        record.setDeliverAt(deliverAt);
        return repository.insert(record) == 1;
    }

    private String toJson(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize MQ event", ex);
        }
    }

    private void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
