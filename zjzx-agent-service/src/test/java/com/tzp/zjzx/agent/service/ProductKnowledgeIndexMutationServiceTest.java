package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.agent.repository.ProductIndexConsumeLogRepository;
import com.tzp.zjzx.agent.repository.ProductVectorDocumentRepository;
import com.tzp.zjzx.ai.contract.event.ProductKnowledgeChangedEvent;
import com.tzp.zjzx.ai.contract.mq.ProductKnowledgeMqConstants;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgeDocumentVo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ProductKnowledgeIndexMutationServiceTest {

    @Test
    void upsertsCurrentDocumentsAndDeletesStaleSku() {
        VectorStore vectorStore = mock(VectorStore.class);
        ProductVectorDocumentRepository vectorRepository =
                mock(ProductVectorDocumentRepository.class);
        ProductIndexConsumeLogRepository consumeRepository =
                mock(ProductIndexConsumeLogRepository.class);
        when(consumeRepository.exists(
                ProductKnowledgeMqConstants.AGENT_CHANGED_CONSUMER,
                "event-1"
        )).thenReturn(false);
        when(vectorRepository.findDocumentIdsByProductId(10L))
                .thenReturn(List.of("product-sku-1", "product-sku-3"));
        when(consumeRepository.markConsumed(
                ProductKnowledgeMqConstants.AGENT_CHANGED_CONSUMER,
                "event-1",
                ProductKnowledgeMqConstants.CHANGED_EVENT_TYPE
        )).thenReturn(true);

        ProductKnowledgeIndexMutationService service =
                new ProductKnowledgeIndexMutationService(
                        provider(vectorStore),
                        new ProductKnowledgeVectorDocumentFactory(),
                        vectorRepository,
                        consumeRepository
                );

        ProductKnowledgeIncrementalIndexResult result = service.apply(
                event("event-1"),
                List.of(document(1L), document(2L))
        );

        assertFalse(result.duplicate());
        assertEquals(2, result.upsertedCount());
        assertEquals(1, result.deletedCount());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> added =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(added.capture());
        assertEquals(
                List.of("product-sku-1", "product-sku-2"),
                added.getValue().stream().map(Document::getId).toList()
        );
        verify(vectorStore).delete(List.of("product-sku-3"));
        verify(consumeRepository).markConsumed(
                ProductKnowledgeMqConstants.AGENT_CHANGED_CONSUMER,
                "event-1",
                ProductKnowledgeMqConstants.CHANGED_EVENT_TYPE
        );
    }

    @Test
    void duplicateEventDoesNotTouchVectorStore() {
        VectorStore vectorStore = mock(VectorStore.class);
        ProductVectorDocumentRepository vectorRepository =
                mock(ProductVectorDocumentRepository.class);
        ProductIndexConsumeLogRepository consumeRepository =
                mock(ProductIndexConsumeLogRepository.class);
        when(consumeRepository.exists(
                ProductKnowledgeMqConstants.AGENT_CHANGED_CONSUMER,
                "event-2"
        )).thenReturn(true);
        ProductKnowledgeIndexMutationService service =
                new ProductKnowledgeIndexMutationService(
                        provider(vectorStore),
                        new ProductKnowledgeVectorDocumentFactory(),
                        vectorRepository,
                        consumeRepository
                );

        ProductKnowledgeIncrementalIndexResult result =
                service.apply(event("event-2"), List.of());

        assertTrue(result.duplicate());
        verifyNoInteractions(vectorRepository);
        verify(vectorStore, never()).add(anyList());
        verify(vectorStore, never()).delete(anyList());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<VectorStore> provider(VectorStore vectorStore) {
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        return provider;
    }

    private ProductKnowledgeChangedEvent event(String eventId) {
        return new ProductKnowledgeChangedEvent(
                eventId,
                10L,
                "UPDATED",
                new Date()
        );
    }

    private ProductKnowledgeDocumentVo document(Long skuId) {
        ProductKnowledgeDocumentVo document =
                new ProductKnowledgeDocumentVo();
        document.setProductId(10L);
        document.setSkuId(skuId);
        document.setProductName("Mac mini");
        document.setSkuName("Mac mini " + skuId);
        document.setContentHash("hash-" + skuId);
        return document;
    }
}
