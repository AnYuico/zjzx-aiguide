package com.tzp.zjzx.agent.task;

import com.tzp.zjzx.agent.config.ProductKnowledgeMqProperties;
import com.tzp.zjzx.agent.service.ProductKnowledgeIndexService;
import com.tzp.zjzx.agent.service.ProductKnowledgeIndexStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConditionalOnProperty(
        prefix = "zjzx.agent.product-index-mq",
        name = "reconciliation-enabled",
        havingValue = "true"
)
public class ProductKnowledgeReconciliationTask {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(ProductKnowledgeReconciliationTask.class);

    private final ProductKnowledgeIndexService indexService;
    private final ProductKnowledgeMqProperties properties;

    public ProductKnowledgeReconciliationTask(
            ProductKnowledgeIndexService indexService,
            ProductKnowledgeMqProperties properties) {
        this.indexService = indexService;
        this.properties = properties;
    }

    @Scheduled(
            initialDelayString =
                    "${zjzx.agent.product-index-mq." +
                            "reconciliation-initial-delay-ms:300000}",
            fixedDelayString =
                    "${zjzx.agent.product-index-mq." +
                            "reconciliation-fixed-delay-ms:21600000}"
    )
    public void reconcile() {
        try {
            Duration timeout = properties.getReconciliationTimeout();
            if (timeout == null || timeout.isZero() || timeout.isNegative()) {
                throw new IllegalStateException(
                        "Product index reconciliation timeout is invalid"
                );
            }
            ProductKnowledgeIndexStatus status =
                    indexService.rebuild().block(timeout);
            if (status != null) {
                LOGGER.info(
                        "Product knowledge reconciliation completed: state={}, "
                                + "indexedCount={}",
                        status.state(),
                        status.indexedCount()
                );
            }
        } catch (RuntimeException exception) {
            LOGGER.error(
                    "Product knowledge reconciliation failed",
                    exception
            );
        }
    }
}
