package com.tzp.zjzx.agent.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzp.zjzx.agent.service.ProductKnowledgeIncrementalIndexResult;
import com.tzp.zjzx.agent.service.ProductKnowledgeIncrementalIndexService;
import com.tzp.zjzx.ai.contract.event.ProductKnowledgeChangedEvent;
import com.tzp.zjzx.ai.contract.mq.ProductKnowledgeMqConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@ConditionalOnProperty(
        prefix = "zjzx.agent.product-index-mq",
        name = "enabled",
        havingValue = "true"
)
public class ProductKnowledgeMqListener {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ProductKnowledgeMqListener.class);

    private final ObjectMapper objectMapper;
    private final ProductKnowledgeIncrementalIndexService incrementalIndexService;

    public ProductKnowledgeMqListener(
            ObjectMapper objectMapper,
            ProductKnowledgeIncrementalIndexService incrementalIndexService) {
        this.objectMapper = objectMapper;
        this.incrementalIndexService = incrementalIndexService;
    }

    @RabbitListener(
            queues = ProductKnowledgeMqConstants.AGENT_CHANGED_QUEUE
    )
    public void onProductKnowledgeChanged(Message message) {
        ProductKnowledgeChangedEvent event = parse(message);
        ProductKnowledgeIncrementalIndexResult result =
                incrementalIndexService.apply(event).block();
        if (result != null) {
            LOGGER.info(
                    "Product knowledge event applied: eventId={}, productId={}, "
                            + "upserted={}, deleted={}, duplicate={}",
                    result.eventId(),
                    result.productId(),
                    result.upsertedCount(),
                    result.deletedCount(),
                    result.duplicate()
            );
        }
    }

    private ProductKnowledgeChangedEvent parse(Message message) {
        try {
            return objectMapper.readValue(
                    message.getBody(),
                    ProductKnowledgeChangedEvent.class
            );
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Invalid product knowledge MQ event",
                    exception
            );
        }
    }
}
