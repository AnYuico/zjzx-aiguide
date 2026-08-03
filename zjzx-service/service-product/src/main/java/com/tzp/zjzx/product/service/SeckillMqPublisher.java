package com.tzp.zjzx.product.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzp.zjzx.model.event.seckill.SeckillOrderRequestedEvent;
import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.utils.SeckillRedisKeys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SeckillMqPublisher {

    private static final long CONFIRM_TIMEOUT_SECONDS = 5;

    private final RabbitTemplate rabbitTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public SeckillMqPublisher(RabbitTemplate rabbitTemplate,
                              StringRedisTemplate redisTemplate,
                              ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public boolean publish(SeckillOrderRequestedEvent event) {
        try {
            CorrelationData correlationData = new CorrelationData(event.getEventId());
            rabbitTemplate.send(RabbitMqConstants.SECKILL_EVENT_EXCHANGE,
                    RabbitMqConstants.SECKILL_ORDER_REQUESTED,
                    buildMessage(event), correlationData);
            CorrelationData.Confirm confirm = correlationData.getFuture()
                    .get(CONFIRM_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!confirm.isAck() || correlationData.getReturned() != null) {
                log.warn("Seckill message was not accepted: requestId={}, reason={}",
                        event.getRequestId(), confirm.getReason());
                recordAttempt(event);
                return false;
            }
            redisTemplate.opsForZSet().remove(
                    SeckillRedisKeys.pending(event.getActivityId(), event.getSkuId()),
                    event.getRequestId());
            redisTemplate.opsForHash().delete(
                    SeckillRedisKeys.attempts(event.getActivityId(), event.getSkuId()),
                    event.getRequestId());
            return true;
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            recordAttempt(event);
            log.warn("Seckill message publish failed: requestId={}",
                    event.getRequestId(), ex);
            return false;
        }
    }

    private Message buildMessage(SeckillOrderRequestedEvent event)
            throws JsonProcessingException {
        byte[] body = objectMapper.writeValueAsString(event)
                .getBytes(StandardCharsets.UTF_8);
        return MessageBuilder.withBody(body)
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(event.getEventId())
                .setType(RabbitMqConstants.SECKILL_ORDER_EVENT)
                .setHeader("x-event-id", event.getEventId())
                .build();
    }

    private void recordAttempt(SeckillOrderRequestedEvent event) {
        redisTemplate.opsForHash().increment(
                SeckillRedisKeys.attempts(event.getActivityId(), event.getSkuId()),
                event.getRequestId(), 1L);
    }
}

