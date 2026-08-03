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
public class ProductRabbitConfiguration {

    @Bean
    public TopicExchange orderEventExchange() {
        return new TopicExchange(RabbitMqConstants.ORDER_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange orderDeadExchange() {
        return new TopicExchange(RabbitMqConstants.ORDER_DEAD_EXCHANGE, true, false);
    }

    @Bean
    public Queue productInventoryConfirmQueue() {
        return businessQueue(RabbitMqConstants.PRODUCT_INVENTORY_CONFIRM_QUEUE,
                RabbitMqConstants.PRODUCT_INVENTORY_CONFIRM_DEAD);
    }

    @Bean
    public Queue productInventoryReleaseQueue() {
        return businessQueue(RabbitMqConstants.PRODUCT_INVENTORY_RELEASE_QUEUE,
                RabbitMqConstants.PRODUCT_INVENTORY_RELEASE_DEAD);
    }

    @Bean
    public Queue productInventoryConfirmDeadQueue() {
        return QueueBuilder.durable(
                RabbitMqConstants.PRODUCT_INVENTORY_CONFIRM_QUEUE + ".dlq").build();
    }

    @Bean
    public Queue productInventoryReleaseDeadQueue() {
        return QueueBuilder.durable(
                RabbitMqConstants.PRODUCT_INVENTORY_RELEASE_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding productInventoryConfirmBinding(
            @Qualifier("productInventoryConfirmQueue") Queue productInventoryConfirmQueue,
            @Qualifier("orderEventExchange") TopicExchange orderEventExchange) {
        return BindingBuilder.bind(productInventoryConfirmQueue).to(orderEventExchange)
                .with(RabbitMqConstants.INVENTORY_CONFIRM_REQUESTED);
    }

    @Bean
    public Binding productInventoryReleaseBinding(
            @Qualifier("productInventoryReleaseQueue") Queue productInventoryReleaseQueue,
            @Qualifier("orderEventExchange") TopicExchange orderEventExchange) {
        return BindingBuilder.bind(productInventoryReleaseQueue).to(orderEventExchange)
                .with(RabbitMqConstants.INVENTORY_RELEASE_REQUESTED);
    }

    @Bean
    public Binding productInventoryConfirmDeadBinding(
            @Qualifier("productInventoryConfirmDeadQueue") Queue productInventoryConfirmDeadQueue,
            @Qualifier("orderDeadExchange") TopicExchange orderDeadExchange) {
        return BindingBuilder.bind(productInventoryConfirmDeadQueue).to(orderDeadExchange)
                .with(RabbitMqConstants.PRODUCT_INVENTORY_CONFIRM_DEAD);
    }

    @Bean
    public Binding productInventoryReleaseDeadBinding(
            @Qualifier("productInventoryReleaseDeadQueue") Queue productInventoryReleaseDeadQueue,
            @Qualifier("orderDeadExchange") TopicExchange orderDeadExchange) {
        return BindingBuilder.bind(productInventoryReleaseDeadQueue).to(orderDeadExchange)
                .with(RabbitMqConstants.PRODUCT_INVENTORY_RELEASE_DEAD);
    }

    private Queue businessQueue(String name, String deadRoutingKey) {
        return QueueBuilder.durable(name)
                .deadLetterExchange(RabbitMqConstants.ORDER_DEAD_EXCHANGE)
                .deadLetterRoutingKey(deadRoutingKey)
                .build();
    }
}
