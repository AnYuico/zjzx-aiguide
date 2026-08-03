package com.tzp.zjzx.order.config;

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
public class OrderRabbitConfiguration {

    @Bean
    public TopicExchange orderEventExchange() {
        return new TopicExchange(RabbitMqConstants.ORDER_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange orderDeadExchange() {
        return new TopicExchange(RabbitMqConstants.ORDER_DEAD_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange seckillEventExchange() {
        return new TopicExchange(RabbitMqConstants.SECKILL_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange seckillDeadExchange() {
        return new TopicExchange(RabbitMqConstants.SECKILL_DEAD_EXCHANGE, true, false);
    }

    @Bean
    public Queue orderPaymentQueue() {
        return businessQueue(RabbitMqConstants.ORDER_PAYMENT_QUEUE,
                RabbitMqConstants.ORDER_PAYMENT_DEAD);
    }

    @Bean
    public Queue orderTimeoutDelayQueue() {
        return QueueBuilder.durable(RabbitMqConstants.ORDER_TIMEOUT_DELAY_QUEUE)
                .deadLetterExchange(RabbitMqConstants.ORDER_EVENT_EXCHANGE)
                .deadLetterRoutingKey(RabbitMqConstants.ORDER_TIMEOUT_CHECK)
                .build();
    }

    @Bean
    public Queue orderTimeoutQueue() {
        return businessQueue(RabbitMqConstants.ORDER_TIMEOUT_QUEUE,
                RabbitMqConstants.ORDER_TIMEOUT_DEAD);
    }

    @Bean
    public Queue orderInventoryCompletedQueue() {
        return businessQueue(RabbitMqConstants.ORDER_INVENTORY_COMPLETED_QUEUE,
                RabbitMqConstants.ORDER_INVENTORY_COMPLETED_DEAD);
    }

    @Bean
    public Queue orderPaymentDeadQueue() {
        return QueueBuilder.durable(RabbitMqConstants.ORDER_PAYMENT_QUEUE + ".dlq").build();
    }

    @Bean
    public Queue orderTimeoutDeadQueue() {
        return QueueBuilder.durable(RabbitMqConstants.ORDER_TIMEOUT_QUEUE + ".dlq").build();
    }

    @Bean
    public Queue orderInventoryCompletedDeadQueue() {
        return QueueBuilder.durable(
                RabbitMqConstants.ORDER_INVENTORY_COMPLETED_QUEUE + ".dlq").build();
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
    public Binding orderPaymentBinding(
            @Qualifier("orderPaymentQueue") Queue orderPaymentQueue,
            @Qualifier("orderEventExchange") TopicExchange orderEventExchange) {
        return BindingBuilder.bind(orderPaymentQueue).to(orderEventExchange)
                .with(RabbitMqConstants.PAYMENT_SUCCEEDED);
    }

    @Bean
    public Binding orderTimeoutDelayBinding(
            @Qualifier("orderTimeoutDelayQueue") Queue orderTimeoutDelayQueue,
            @Qualifier("orderEventExchange") TopicExchange orderEventExchange) {
        return BindingBuilder.bind(orderTimeoutDelayQueue).to(orderEventExchange)
                .with(RabbitMqConstants.ORDER_TIMEOUT_DELAY);
    }

    @Bean
    public Binding orderTimeoutBinding(
            @Qualifier("orderTimeoutQueue") Queue orderTimeoutQueue,
            @Qualifier("orderEventExchange") TopicExchange orderEventExchange) {
        return BindingBuilder.bind(orderTimeoutQueue).to(orderEventExchange)
                .with(RabbitMqConstants.ORDER_TIMEOUT_CHECK);
    }

    @Bean
    public Binding orderInventoryCompletedBinding(
            @Qualifier("orderInventoryCompletedQueue") Queue orderInventoryCompletedQueue,
            @Qualifier("orderEventExchange") TopicExchange orderEventExchange) {
        return BindingBuilder.bind(orderInventoryCompletedQueue).to(orderEventExchange)
                .with(RabbitMqConstants.INVENTORY_OPERATION_COMPLETED);
    }

    @Bean
    public Binding orderPaymentDeadBinding(
            @Qualifier("orderPaymentDeadQueue") Queue orderPaymentDeadQueue,
            @Qualifier("orderDeadExchange") TopicExchange orderDeadExchange) {
        return BindingBuilder.bind(orderPaymentDeadQueue).to(orderDeadExchange)
                .with(RabbitMqConstants.ORDER_PAYMENT_DEAD);
    }

    @Bean
    public Binding orderTimeoutDeadBinding(
            @Qualifier("orderTimeoutDeadQueue") Queue orderTimeoutDeadQueue,
            @Qualifier("orderDeadExchange") TopicExchange orderDeadExchange) {
        return BindingBuilder.bind(orderTimeoutDeadQueue).to(orderDeadExchange)
                .with(RabbitMqConstants.ORDER_TIMEOUT_DEAD);
    }

    @Bean
    public Binding orderInventoryCompletedDeadBinding(
            @Qualifier("orderInventoryCompletedDeadQueue") Queue orderInventoryCompletedDeadQueue,
            @Qualifier("orderDeadExchange") TopicExchange orderDeadExchange) {
        return BindingBuilder.bind(orderInventoryCompletedDeadQueue).to(orderDeadExchange)
                .with(RabbitMqConstants.ORDER_INVENTORY_COMPLETED_DEAD);
    }

    @Bean
    public Binding seckillOrderBinding(
            @Qualifier("seckillOrderQueue") Queue seckillOrderQueue,
            @Qualifier("seckillEventExchange") TopicExchange seckillEventExchange) {
        return BindingBuilder.bind(seckillOrderQueue).to(seckillEventExchange)
                .with(RabbitMqConstants.SECKILL_ORDER_REQUESTED);
    }

    @Bean
    public Binding seckillOrderDeadBinding(
            @Qualifier("seckillOrderDeadQueue") Queue seckillOrderDeadQueue,
            @Qualifier("seckillDeadExchange") TopicExchange seckillDeadExchange) {
        return BindingBuilder.bind(seckillOrderDeadQueue).to(seckillDeadExchange)
                .with(RabbitMqConstants.SECKILL_ORDER_DEAD);
    }

    private Queue businessQueue(String name, String deadRoutingKey) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(RabbitMqConstants.ORDER_DEAD_EXCHANGE)
                .deadLetterRoutingKey(deadRoutingKey)
                .build();
    }
}
