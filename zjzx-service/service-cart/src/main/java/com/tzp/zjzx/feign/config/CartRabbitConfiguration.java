package com.tzp.zjzx.feign.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.mq.consume.MqMessageParser;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration(proxyBeanMethods = false)
public class CartRabbitConfiguration {

    @Bean
    public TopicExchange cartOrderEventExchange() {
        return new TopicExchange(RabbitMqConstants.ORDER_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange cartOrderDeadExchange() {
        return new TopicExchange(RabbitMqConstants.ORDER_DEAD_EXCHANGE, true, false);
    }

    @Bean
    public Queue cartCleanupQueue() {
        return QueueBuilder.durable(RabbitMqConstants.CART_CLEANUP_QUEUE)
                .deadLetterExchange(RabbitMqConstants.ORDER_DEAD_EXCHANGE)
                .deadLetterRoutingKey(RabbitMqConstants.CART_CLEANUP_DEAD)
                .build();
    }

    @Bean
    public Queue cartCleanupDeadQueue() {
        return QueueBuilder.durable(RabbitMqConstants.CART_CLEANUP_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding cartCleanupBinding(
            @Qualifier("cartCleanupQueue") Queue cartCleanupQueue,
            @Qualifier("cartOrderEventExchange") TopicExchange orderEventExchange) {
        return BindingBuilder.bind(cartCleanupQueue).to(orderEventExchange)
                .with(RabbitMqConstants.CART_CLEANUP_REQUESTED);
    }

    @Bean
    public Binding cartCleanupDeadBinding(
            @Qualifier("cartCleanupDeadQueue") Queue cartCleanupDeadQueue,
            @Qualifier("cartOrderDeadExchange") TopicExchange orderDeadExchange) {
        return BindingBuilder.bind(cartCleanupDeadQueue).to(orderDeadExchange)
                .with(RabbitMqConstants.CART_CLEANUP_DEAD);
    }

    @Bean
    public MqMessageParser cartMqMessageParser(ObjectMapper objectMapper) {
        return new MqMessageParser(objectMapper);
    }

    @Bean
    public RedisScript<Long> cartCleanupScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("redis/cart_cleanup.lua")));
        script.setResultType(Long.class);
        return script;
    }
}
