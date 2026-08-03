package com.tzp.zjzx.manager.config;

import com.tzp.zjzx.ai.contract.mq.ProductKnowledgeMqConstants;
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
public class ManagerRabbitConfiguration {

    @Bean
    public TopicExchange orderEventExchange() {
        return new TopicExchange(RabbitMqConstants.ORDER_EVENT_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange orderDeadExchange() {
        return new TopicExchange(RabbitMqConstants.ORDER_DEAD_EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange productKnowledgeEventExchange() {
        return new TopicExchange(
                ProductKnowledgeMqConstants.EVENT_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    public TopicExchange productKnowledgeDeadExchange() {
        return new TopicExchange(
                ProductKnowledgeMqConstants.DEAD_EXCHANGE,
                true,
                false
        );
    }

    @Bean
    public Queue managerOrderPaidQueue() {
        return QueueBuilder.durable(RabbitMqConstants.MANAGER_ORDER_PAID_QUEUE)
                .deadLetterExchange(RabbitMqConstants.ORDER_DEAD_EXCHANGE)
                .deadLetterRoutingKey(RabbitMqConstants.MANAGER_ORDER_PAID_DEAD)
                .build();
    }

    @Bean
    public Queue managerOrderPaidDeadQueue() {
        return QueueBuilder.durable(RabbitMqConstants.MANAGER_ORDER_PAID_QUEUE + ".dlq").build();
    }

    @Bean
    public Binding managerOrderPaidBinding(
            @Qualifier("managerOrderPaidQueue") Queue managerOrderPaidQueue,
            @Qualifier("orderEventExchange") TopicExchange orderEventExchange) {
        return BindingBuilder.bind(managerOrderPaidQueue).to(orderEventExchange)
                .with(RabbitMqConstants.ORDER_PAID);
    }

    @Bean
    public Binding managerOrderPaidDeadBinding(
            @Qualifier("managerOrderPaidDeadQueue") Queue managerOrderPaidDeadQueue,
            @Qualifier("orderDeadExchange") TopicExchange orderDeadExchange) {
        return BindingBuilder.bind(managerOrderPaidDeadQueue).to(orderDeadExchange)
                .with(RabbitMqConstants.MANAGER_ORDER_PAID_DEAD);
    }
}
