package com.tzp.zjzx.product.config;

import com.tzp.zjzx.mq.RabbitMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class SeckillRabbitConfiguration {

    @Bean
    public TopicExchange seckillEventExchange() {
        return new TopicExchange(RabbitMqConstants.SECKILL_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange seckillDeadExchange() {
        return new TopicExchange(RabbitMqConstants.SECKILL_DEAD_EXCHANGE, true, false);
    }

    @Bean
    public Queue seckillOrderQueue() {
        return QueueBuilder.durable(RabbitMqConstants.SECKILL_ORDER_QUEUE)
                .deadLetterExchange(RabbitMqConstants.SECKILL_DEAD_EXCHANGE)
                .deadLetterRoutingKey(RabbitMqConstants.SECKILL_ORDER_DEAD)
                .build();
    }

    @Bean
    public Queue seckillOrderDeadQueue() {
        return QueueBuilder.durable(
                RabbitMqConstants.SECKILL_ORDER_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding seckillOrderBinding(
            @Qualifier("seckillOrderQueue") Queue queue,
            @Qualifier("seckillEventExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange)
                .with(RabbitMqConstants.SECKILL_ORDER_REQUESTED);
    }

    @Bean
    public Binding seckillOrderDeadBinding(
            @Qualifier("seckillOrderDeadQueue") Queue queue,
            @Qualifier("seckillDeadExchange") TopicExchange exchange) {
        return BindingBuilder.bind(queue).to(exchange)
                .with(RabbitMqConstants.SECKILL_ORDER_DEAD);
    }
}

