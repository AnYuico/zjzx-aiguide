package com.tzp.zjzx.mq.consume;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;

import java.io.IOException;

public class MqMessageParser {

    private final ObjectMapper objectMapper;

    public MqMessageParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T read(Message message, Class<T> eventType) {
        try {
            return objectMapper.readValue(message.getBody(), eventType);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid MQ event payload", ex);
        }
    }
}
