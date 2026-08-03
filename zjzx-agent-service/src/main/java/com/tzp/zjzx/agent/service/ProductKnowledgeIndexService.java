package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.agent.client.ProductGuideCatalogClient;
import com.tzp.zjzx.agent.config.ProductRetrievalProperties;
import com.tzp.zjzx.ai.contract.dto.ProductKnowledgePageQueryDto;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgeDocumentVo;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgePageVo;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class ProductKnowledgeIndexService {

    private final ProductGuideCatalogClient productGuideCatalogClient;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ProductRetrievalProperties properties;
    private final ProductKnowledgeVectorDocumentFactory documentFactory;
    private final ProductKnowledgeIndexCoordinator indexCoordinator;
    private final AtomicBoolean rebuilding = new AtomicBoolean();
    private final AtomicReference<ProductKnowledgeIndexStatus> status =
            new AtomicReference<>(ProductKnowledgeIndexStatus.idle());

    public ProductKnowledgeIndexService(ProductGuideCatalogClient productGuideCatalogClient,
                                        ObjectProvider<VectorStore> vectorStoreProvider,
                                        ProductRetrievalProperties properties,
                                        ProductKnowledgeVectorDocumentFactory documentFactory,
                                        ProductKnowledgeIndexCoordinator indexCoordinator) {
        this.productGuideCatalogClient = productGuideCatalogClient;
        this.vectorStoreProvider = vectorStoreProvider;
        this.properties = properties;
        this.documentFactory = documentFactory;
        this.indexCoordinator = indexCoordinator;
    }

    public Mono<ProductKnowledgeIndexStatus> rebuild() {
        return Mono.defer(() -> {
            VectorStore vectorStore = requireVectorStore();
            validateConfiguration();
            if (!rebuilding.compareAndSet(false, true)) {
                return Mono.error(new IllegalStateException(
                        "Product knowledge index rebuild is already running"
                ));
            }

            Instant startedAt = Instant.now();
            status.set(new ProductKnowledgeIndexStatus(
                    ProductKnowledgeIndexStatus.State.RUNNING,
                    0,
                    startedAt,
                    null,
                    "Loading product knowledge documents"
            ));

            return indexCoordinator.serialize(() -> loadAllDocuments()
                            .publishOn(Schedulers.boundedElastic())
                            .map(documents -> rebuildVectorStore(
                                    vectorStore,
                                    documents,
                                    startedAt
                            )))
                    .doOnError(failure -> status.set(new ProductKnowledgeIndexStatus(
                            ProductKnowledgeIndexStatus.State.FAILED,
                            0,
                            startedAt,
                            Instant.now(),
                            safeFailureMessage(failure)
                    )))
                    .doFinally(signal -> rebuilding.set(false));
        });
    }

    public ProductKnowledgeIndexStatus status() {
        if (!properties.isVectorEnabled()) {
            return new ProductKnowledgeIndexStatus(
                    ProductKnowledgeIndexStatus.State.DISABLED,
                    0,
                    null,
                    null,
                    "Vector retrieval is disabled"
            );
        }
        return status.get();
    }

    private Mono<List<ProductKnowledgeDocumentVo>> loadAllDocuments() {
        return fetchPage(0L)
                .expand(page -> page.isHasMore()
                        ? fetchPage(requireNextCursor(page))
                        : Mono.empty())
                .concatMapIterable(this::safeItems)
                .collectList()
                .map(items -> {
                    if (items.size() > properties.getMaxIndexDocuments()) {
                        throw new IllegalStateException(
                                "Product document count exceeds configured safety limit"
                        );
                    }
                    return items;
                });
    }

    private Mono<ProductKnowledgePageVo> fetchPage(long afterSkuId) {
        ProductKnowledgePageQueryDto query = new ProductKnowledgePageQueryDto();
        query.setAfterSkuId(afterSkuId);
        query.setLimit(properties.getIndexPageSize());
        return productGuideCatalogClient.getKnowledgePage(query)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Product service returned an empty knowledge page response"
                )));
    }

    private long requireNextCursor(ProductKnowledgePageVo page) {
        if (page.getNextCursor() == null || page.getNextCursor() <= 0) {
            throw new IllegalStateException("Product knowledge page cursor is invalid");
        }
        return page.getNextCursor();
    }

    private Iterable<ProductKnowledgeDocumentVo> safeItems(ProductKnowledgePageVo page) {
        return page.getItems() == null ? List.of() : page.getItems();
    }

    private ProductKnowledgeIndexStatus rebuildVectorStore(
            VectorStore vectorStore,
            List<ProductKnowledgeDocumentVo> sourceDocuments,
            Instant startedAt) {
        String generation = UUID.randomUUID().toString();
        List<Document> documents = sourceDocuments.stream()
                .map(source -> documentFactory.create(source, generation))
                .toList();

        if (documents.isEmpty()) {
            vectorStore.delete("documentType == '"
                    + ProductKnowledgeVectorDocumentFactory.DOCUMENT_TYPE + "'");
        } else {
            for (int offset = 0; offset < documents.size();
                 offset += properties.getIndexPageSize()) {
                int toIndex = Math.min(
                        offset + properties.getIndexPageSize(),
                        documents.size()
                );
                vectorStore.add(documents.subList(offset, toIndex));
                status.set(new ProductKnowledgeIndexStatus(
                        ProductKnowledgeIndexStatus.State.RUNNING,
                        toIndex,
                        startedAt,
                        null,
                        "Writing product embeddings"
                ));
            }
            vectorStore.delete(
                    "documentType == '"
                            + ProductKnowledgeVectorDocumentFactory.DOCUMENT_TYPE
                            + "' && indexGeneration != '" + generation + "'"
            );
        }

        ProductKnowledgeIndexStatus completed = new ProductKnowledgeIndexStatus(
                ProductKnowledgeIndexStatus.State.SUCCEEDED,
                documents.size(),
                startedAt,
                Instant.now(),
                "Product knowledge index rebuild completed"
        );
        status.set(completed);
        return completed;
    }

    private VectorStore requireVectorStore() {
        if (!properties.isVectorEnabled()) {
            throw new IllegalStateException("Vector retrieval is disabled");
        }
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new IllegalStateException(
                    "VectorStore is unavailable; enable Ollama embedding and PGvector"
            );
        }
        return vectorStore;
    }

    private void validateConfiguration() {
        if (properties.getIndexPageSize() < 1
                || properties.getIndexPageSize() > 500) {
            throw new IllegalStateException("Index page size must be between 1 and 500");
        }
        if (properties.getMaxIndexDocuments() < properties.getIndexPageSize()) {
            throw new IllegalStateException(
                    "Max index documents must not be smaller than index page size"
            );
        }
    }

    private String safeFailureMessage(Throwable failure) {
        return StringUtils.hasText(failure.getMessage())
                ? failure.getMessage()
                : failure.getClass().getSimpleName();
    }
}
