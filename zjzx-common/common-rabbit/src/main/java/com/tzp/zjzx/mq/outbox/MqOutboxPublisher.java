package com.tzp.zjzx.mq.outbox;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageBuilderSupport;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MqOutboxPublisher {

    private final MqOutboxRepository repository;
    private final RabbitTemplate rabbitTemplate;
    private final MqOutboxProperties properties;
    private final String producer;

    public MqOutboxPublisher(MqOutboxRepository repository,
                             RabbitTemplate rabbitTemplate,
                             MqOutboxProperties properties,
                             String producer) {
        this.repository = repository;
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
        this.producer = producer;
    }

    @Scheduled(fixedDelayString = "${zjzx.mq.outbox.publisher-delay-ms:1000}",
            initialDelayString = "${zjzx.mq.outbox.publisher-initial-delay-ms:5000}")
    public void publishPending() {
        List<MqOutboxRecord> records = repository.findPending(producer, properties.getBatchSize());
        for (MqOutboxRecord record : records) {
            publish(record);
        }
    }

    private void publish(MqOutboxRecord record) {
        try {
            CorrelationData correlationData = new CorrelationData(record.getEventId());
            rabbitTemplate.send(record.getExchangeName(), record.getRoutingKey(),
                    buildMessage(record), correlationData);

            CorrelationData.Confirm confirm = correlationData.getFuture().get(
                    properties.getConfirmTimeoutMillis(), TimeUnit.MILLISECONDS);
            if (!confirm.isAck()) {
                throw new IllegalStateException("RabbitMQ rejected message: " + confirm.getReason());
            }
            if (correlationData.getReturned() != null) {
                throw new IllegalStateException("RabbitMQ returned unroutable message");
            }
            repository.markSent(record.getId());
        } catch (Exception ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            int retryCount = record.getRetryCount() + 1;
            boolean dead = retryCount >= properties.getMaxRetries();
            long delaySeconds = Math.min(300L, 1L << Math.min(retryCount, 8));
            repository.markRetry(record.getId(), retryCount,
                    new Date(System.currentTimeMillis() + delaySeconds * 1000L),
                    abbreviate(ex.getMessage()), dead);
            log.warn("MQ outbox publish failed: eventId={}, retry={}, dead={}",
                    record.getEventId(), retryCount, dead, ex);
        }
    }

    private Message buildMessage(MqOutboxRecord record) {
        MessageBuilderSupport<Message> builder = MessageBuilder
                .withBody(record.getPayload().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setContentEncoding(StandardCharsets.UTF_8.name())
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(record.getEventId())
                .setType(record.getEventType())
                .setHeader("x-event-id", record.getEventId());
        if (record.getDeliverAt() != null) {
            long expiration = Math.max(1L,
                    record.getDeliverAt().getTime() - System.currentTimeMillis());
            builder.setExpiration(Long.toString(expiration));
        }
        return builder.build();
    }

    private String abbreviate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
