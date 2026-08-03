package com.tzp.zjzx.agent.config;

import com.tzp.zjzx.ai.contract.mq.ProductKnowledgeMqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
        prefix = "zjzx.agent.product-index-mq",
        name = "enabled",
        havingValue = "true"
)
public class ProductKnowledgeRabbitConfiguration {

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
    public Queue agentProductKnowledgeChangedQueue() {
        return QueueBuilder.durable(
                        ProductKnowledgeMqConstants.AGENT_CHANGED_QUEUE
                )
                .deadLetterExchange(
                        ProductKnowledgeMqConstants.DEAD_EXCHANGE
                )
                .deadLetterRoutingKey(
                        ProductKnowledgeMqConstants.CHANGED_DEAD_ROUTING_KEY
                )
                .build();
    }

    @Bean
    public Queue agentProductKnowledgeChangedDeadQueue() {
        return QueueBuilder.durable(
                ProductKnowledgeMqConstants.AGENT_CHANGED_DEAD_QUEUE
        ).build();
    }

    @Bean
    public Binding agentProductKnowledgeChangedBinding(
            @Qualifier("agentProductKnowledgeChangedQueue")
            Queue changedQueue,
            @Qualifier("productKnowledgeEventExchange")
            TopicExchange eventExchange) {
        return BindingBuilder.bind(changedQueue)
                .to(eventExchange)
                .with(ProductKnowledgeMqConstants.CHANGED_ROUTING_KEY);
    }

    @Bean
    public Binding agentProductKnowledgeChangedDeadBinding(
            @Qualifier("agentProductKnowledgeChangedDeadQueue")
            Queue deadQueue,
            @Qualifier("productKnowledgeDeadExchange")
            TopicExchange deadExchange) {
        return BindingBuilder.bind(deadQueue)
                .to(deadExchange)
                .with(ProductKnowledgeMqConstants.CHANGED_DEAD_ROUTING_KEY);
    }
}
