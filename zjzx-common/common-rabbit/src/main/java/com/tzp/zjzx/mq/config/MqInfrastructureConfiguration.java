package com.tzp.zjzx.mq.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzp.zjzx.mq.consume.MqConsumeLogRepository;
import com.tzp.zjzx.mq.consume.MqMessageParser;
import com.tzp.zjzx.mq.outbox.MqOutboxProperties;
import com.tzp.zjzx.mq.outbox.MqOutboxPublisher;
import com.tzp.zjzx.mq.outbox.MqOutboxRepository;
import com.tzp.zjzx.mq.outbox.MqOutboxService;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(MqOutboxProperties.class)
public class MqInfrastructureConfiguration {

    @Bean
    public MqOutboxRepository mqOutboxRepository(JdbcTemplate jdbcTemplate) {
        return new MqOutboxRepository(jdbcTemplate);
    }

    @Bean
    public MqConsumeLogRepository mqConsumeLogRepository(JdbcTemplate jdbcTemplate) {
        return new MqConsumeLogRepository(jdbcTemplate);
    }

    @Bean
    public MqMessageParser mqMessageParser(ObjectMapper objectMapper) {
        return new MqMessageParser(objectMapper);
    }

    @Bean
    public MqOutboxService mqOutboxService(MqOutboxRepository repository,
                                           ObjectMapper objectMapper,
                                           @Value("${spring.application.name}") String producer) {
        return new MqOutboxService(repository, objectMapper, producer);
    }

    @Bean
    @ConditionalOnProperty(prefix = "zjzx.mq.outbox", name = "enabled", havingValue = "true")
    public MqOutboxPublisher mqOutboxPublisher(MqOutboxRepository repository,
                                               RabbitTemplate rabbitTemplate,
                                               MqOutboxProperties properties,
                                               @Value("${spring.application.name}") String producer) {
        return new MqOutboxPublisher(repository, rabbitTemplate, properties, producer);
    }
}
