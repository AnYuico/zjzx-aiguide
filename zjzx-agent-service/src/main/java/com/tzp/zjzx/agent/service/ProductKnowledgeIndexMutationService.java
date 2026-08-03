package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.agent.repository.ProductIndexConsumeLogRepository;
import com.tzp.zjzx.agent.repository.ProductVectorDocumentRepository;
import com.tzp.zjzx.ai.contract.event.ProductKnowledgeChangedEvent;
import com.tzp.zjzx.ai.contract.mq.ProductKnowledgeMqConstants;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgeDocumentVo;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ProductKnowledgeIndexMutationService {

    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ProductKnowledgeVectorDocumentFactory documentFactory;
    private final ProductVectorDocumentRepository vectorDocumentRepository;
    private final ProductIndexConsumeLogRepository consumeLogRepository;

    public ProductKnowledgeIndexMutationService(
            ObjectProvider<VectorStore> vectorStoreProvider,
            ProductKnowledgeVectorDocumentFactory documentFactory,
            ProductVectorDocumentRepository vectorDocumentRepository,
            ProductIndexConsumeLogRepository consumeLogRepository) {
        this.vectorStoreProvider = vectorStoreProvider;
        this.documentFactory = documentFactory;
        this.vectorDocumentRepository = vectorDocumentRepository;
        this.consumeLogRepository = consumeLogRepository;
    }

    public ProductKnowledgeIncrementalIndexResult apply(
            ProductKnowledgeChangedEvent event,
            List<ProductKnowledgeDocumentVo> sourceDocuments) {
        if (isConsumed(event.getEventId())) {
            return ProductKnowledgeIncrementalIndexResult.duplicate(
                    event.getEventId(),
                    event.getProductId()
            );
        }

        VectorStore vectorStore = requireVectorStore();
        List<String> existingIds =
                vectorDocumentRepository.findDocumentIdsByProductId(
                        event.getProductId()
                );
        Map<String, Document> targetDocuments = targetDocuments(
                event,
                sourceDocuments
        );

        if (!targetDocuments.isEmpty()) {
            vectorStore.add(new ArrayList<>(targetDocuments.values()));
        }

        Set<String> staleIds = new LinkedHashSet<>(existingIds);
        staleIds.removeAll(targetDocuments.keySet());
        if (!staleIds.isEmpty()) {
            vectorStore.delete(new ArrayList<>(staleIds));
        }

        consumeLogRepository.markConsumed(
                ProductKnowledgeMqConstants.AGENT_CHANGED_CONSUMER,
                event.getEventId(),
                ProductKnowledgeMqConstants.CHANGED_EVENT_TYPE
        );
        return new ProductKnowledgeIncrementalIndexResult(
                event.getEventId(),
                event.getProductId(),
                targetDocuments.size(),
                staleIds.size(),
                false
        );
    }

    private Map<String, Document> targetDocuments(
            ProductKnowledgeChangedEvent event,
            List<ProductKnowledgeDocumentVo> sourceDocuments) {
        Map<String, Document> documents = new LinkedHashMap<>();
        String generation = "event:" + event.getEventId();
        for (ProductKnowledgeDocumentVo source : sourceDocuments) {
            if (source == null
                    || !event.getProductId().equals(source.getProductId())) {
                throw new IllegalStateException(
                        "Product knowledge snapshot contains another product"
                );
            }
            Document document = documentFactory.create(source, generation);
            documents.put(document.getId(), document);
        }
        return documents;
    }

    private boolean isConsumed(String eventId) {
        return consumeLogRepository.exists(
                ProductKnowledgeMqConstants.AGENT_CHANGED_CONSUMER,
                eventId
        );
    }

    private VectorStore requireVectorStore() {
        VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
        if (vectorStore == null) {
            throw new IllegalStateException(
                    "VectorStore is unavailable for incremental indexing"
            );
        }
        return vectorStore;
    }
}
