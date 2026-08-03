package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.agent.client.ProductGuideCatalogClient;
import com.tzp.zjzx.agent.config.ProductRetrievalProperties;
import com.tzp.zjzx.agent.repository.ProductIndexConsumeLogRepository;
import com.tzp.zjzx.ai.contract.event.ProductKnowledgeChangedEvent;
import com.tzp.zjzx.ai.contract.mq.ProductKnowledgeMqConstants;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Service
public class ProductKnowledgeIncrementalIndexService {

    private final ProductGuideCatalogClient productGuideCatalogClient;
    private final ProductRetrievalProperties retrievalProperties;
    private final ProductIndexConsumeLogRepository consumeLogRepository;
    private final ProductKnowledgeIndexCoordinator indexCoordinator;
    private final ProductKnowledgeIndexMutationService mutationService;

    public ProductKnowledgeIncrementalIndexService(
            ProductGuideCatalogClient productGuideCatalogClient,
            ProductRetrievalProperties retrievalProperties,
            ProductIndexConsumeLogRepository consumeLogRepository,
            ProductKnowledgeIndexCoordinator indexCoordinator,
            ProductKnowledgeIndexMutationService mutationService) {
        this.productGuideCatalogClient = productGuideCatalogClient;
        this.retrievalProperties = retrievalProperties;
        this.consumeLogRepository = consumeLogRepository;
        this.indexCoordinator = indexCoordinator;
        this.mutationService = mutationService;
    }

    public Mono<ProductKnowledgeIncrementalIndexResult> apply(
            ProductKnowledgeChangedEvent event) {
        validate(event);
        if (!retrievalProperties.isVectorEnabled()) {
            return Mono.error(new IllegalStateException(
                    "Vector retrieval is disabled"
            ));
        }
        if (consumeLogRepository.exists(
                ProductKnowledgeMqConstants.AGENT_CHANGED_CONSUMER,
                event.getEventId()
        )) {
            return Mono.just(
                    ProductKnowledgeIncrementalIndexResult.duplicate(
                            event.getEventId(),
                            event.getProductId()
                    )
            );
        }

        return Mono.fromCallable(() -> indexCoordinator.execute(() -> {
                    var documents = productGuideCatalogClient
                            .getKnowledgeByProductId(event.getProductId())
                            .block();
                    if (documents == null) {
                        throw new IllegalStateException(
                                "Product service returned no knowledge snapshot"
                        );
                    }
                    return mutationService.apply(event, documents);
                }))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private void validate(ProductKnowledgeChangedEvent event) {
        if (event == null
                || !StringUtils.hasText(event.getEventId())
                || event.getProductId() == null
                || event.getProductId() <= 0
                || !StringUtils.hasText(event.getReason())
                || event.getChangedAt() == null) {
            throw new IllegalArgumentException(
                    "Invalid product knowledge changed event"
            );
        }
    }
}
