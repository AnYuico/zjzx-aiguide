package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.agent.client.ProductGuideCatalogClient;
import com.tzp.zjzx.agent.config.ProductRetrievalProperties;
import com.tzp.zjzx.ai.contract.dto.ProductKnowledgePageQueryDto;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgeDocumentVo;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgePageVo;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductKnowledgeIndexServiceTest {

    @Test
    void rebuildLoadsEveryPageAndWritesStableDocuments() {
        ProductGuideCatalogClient client = mock(ProductGuideCatalogClient.class);
        VectorStore vectorStore = mock(VectorStore.class);
        ProductRetrievalProperties properties = properties();
        ProductKnowledgeIndexService service = new ProductKnowledgeIndexService(
                client,
                vectorStoreProvider(vectorStore),
                properties,
                new ProductKnowledgeVectorDocumentFactory(),
                new ProductKnowledgeIndexCoordinator()
        );
        when(client.getKnowledgePage(anyPageQuery()))
                .thenAnswer(invocation -> {
                    ProductKnowledgePageQueryDto query = invocation.getArgument(0);
                    if (query.getAfterSkuId() == 0L) {
                        return Mono.just(new ProductKnowledgePageVo(
                                List.of(document(1L), document(2L)),
                                2L,
                                true
                        ));
                    }
                    return Mono.just(new ProductKnowledgePageVo(
                            List.of(document(3L)),
                            3L,
                            false
                    ));
                });

        StepVerifier.create(service.rebuild())
                .assertNext(status -> {
                    assertEquals(
                            ProductKnowledgeIndexStatus.State.SUCCEEDED,
                            status.state()
                    );
                    assertEquals(3, status.indexedCount());
                })
                .verifyComplete();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Document>> documentsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore, times(2)).add(documentsCaptor.capture());
        List<Document> allDocuments = documentsCaptor.getAllValues().stream()
                .flatMap(List::stream)
                .toList();
        assertEquals(3, allDocuments.size());
        assertEquals("product-sku-1", allDocuments.get(0).getId());
        assertEquals(1L, allDocuments.get(0).getMetadata().get("skuId"));
        assertTrue(allDocuments.get(0).getText().contains("Mac Mini"));
        verify(vectorStore).delete(anyString());
    }

    @Test
    void failedWriteKeepsPreviousGeneration() {
        ProductGuideCatalogClient client = mock(ProductGuideCatalogClient.class);
        VectorStore vectorStore = mock(VectorStore.class);
        ProductKnowledgeIndexService service = new ProductKnowledgeIndexService(
                client,
                vectorStoreProvider(vectorStore),
                properties(),
                new ProductKnowledgeVectorDocumentFactory(),
                new ProductKnowledgeIndexCoordinator()
        );
        when(client.getKnowledgePage(anyPageQuery()))
                .thenReturn(Mono.just(new ProductKnowledgePageVo(
                        List.of(document(1L)),
                        1L,
                        false
                )));
        doThrow(new IllegalStateException("embedding failed"))
                .when(vectorStore)
                .add(anyList());

        StepVerifier.create(service.rebuild())
                .expectErrorMatches(failure ->
                        failure.getMessage().contains("embedding failed"))
                .verify();

        verify(vectorStore, never()).delete(anyString());
        assertEquals(
                ProductKnowledgeIndexStatus.State.FAILED,
                service.status().state()
        );
    }

    @Test
    void disabledVectorModeRejectsRebuildWithoutCallingProductService() {
        ProductGuideCatalogClient client = mock(ProductGuideCatalogClient.class);
        ProductRetrievalProperties properties = properties();
        properties.setVectorEnabled(false);
        ProductKnowledgeIndexService service = new ProductKnowledgeIndexService(
                client,
                vectorStoreProvider(mock(VectorStore.class)),
                properties,
                new ProductKnowledgeVectorDocumentFactory(),
                new ProductKnowledgeIndexCoordinator()
        );

        StepVerifier.create(service.rebuild())
                .expectErrorMatches(failure ->
                        failure.getMessage().contains("disabled"))
                .verify();

        verify(client, never()).getKnowledgePage(anyPageQuery());
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<VectorStore> vectorStoreProvider(VectorStore vectorStore) {
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        return provider;
    }

    private ProductRetrievalProperties properties() {
        ProductRetrievalProperties properties = new ProductRetrievalProperties();
        properties.setVectorEnabled(true);
        properties.setIndexPageSize(2);
        properties.setMaxIndexDocuments(10);
        return properties;
    }

    private ProductKnowledgeDocumentVo document(Long skuId) {
        ProductKnowledgeDocumentVo document = new ProductKnowledgeDocumentVo();
        document.setProductId(10L);
        document.setSkuId(skuId);
        document.setCategoryId(20L);
        document.setBrandId(30L);
        document.setProductName("Mac Mini");
        document.setSkuName("Mac Mini 16G");
        document.setBrandName("Apple");
        document.setCategoryPath("数码 > 电脑");
        document.setProductSpec("{\"memory\":\"16G\"}");
        document.setSkuSpec("{\"color\":\"silver\"}");
        document.setUnitName("台");
        document.setContentHash("hash-" + skuId);
        document.setUpdatedAt(new Date());
        return document;
    }

    private ProductKnowledgePageQueryDto anyPageQuery() {
        return org.mockito.ArgumentMatchers.any(ProductKnowledgePageQueryDto.class);
    }
}
