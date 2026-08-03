package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.agent.client.ProductGuideCatalogClient;
import com.tzp.zjzx.agent.config.ProductRetrievalProperties;
import com.tzp.zjzx.ai.contract.dto.ProductGuideQueryDto;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class HybridProductRetrievalServiceTest {

    @Test
    void vectorCandidateIsRealtimeValidatedBeforeBeingReturned() {
        ProductGuideCatalogClient client = mock(ProductGuideCatalogClient.class);
        VectorStore vectorStore = mock(VectorStore.class);
        ObjectProvider<VectorStore> provider = vectorStoreProvider(vectorStore);
        ProductRetrievalProperties properties = enabledProperties();
        HybridProductRetrievalService service = new HybridProductRetrievalService(
                client,
                provider,
                properties
        );
        ProductGuideVo keywordProduct = product(1L, "Mac Mini");
        ProductGuideVo semanticProduct = product(2L, "MacBook Air");
        when(client.search(any(ProductGuideQueryDto.class)))
                .thenReturn(Mono.just(List.of(keywordProduct)));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(
                        vectorDocument(2L),
                        vectorDocument(1L)
                ));
        when(client.getBySkuId(2L)).thenReturn(Mono.just(semanticProduct));

        StepVerifier.create(service.search("适合移动办公的苹果电脑", 5))
                .assertNext(products -> {
                    assertEquals(2, products.size());
                    assertEquals(1L, products.get(0).getSkuId());
                    assertEquals(2L, products.get(1).getSkuId());
                })
                .verifyComplete();

        verify(client).getBySkuId(2L);
        verify(client, never()).getBySkuId(1L);
    }

    @Test
    void staleVectorCandidateIsDiscarded() {
        ProductGuideCatalogClient client = mock(ProductGuideCatalogClient.class);
        VectorStore vectorStore = mock(VectorStore.class);
        HybridProductRetrievalService service = new HybridProductRetrievalService(
                client,
                vectorStoreProvider(vectorStore),
                enabledProperties()
        );
        ProductGuideVo keywordProduct = product(1L, "Mac Mini");
        when(client.search(any(ProductGuideQueryDto.class)))
                .thenReturn(Mono.just(List.of(keywordProduct)));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(vectorDocument(99L)));
        when(client.getBySkuId(99L)).thenReturn(Mono.empty());

        StepVerifier.create(service.search("小型桌面电脑", 5))
                .assertNext(products -> {
                    assertEquals(1, products.size());
                    assertEquals(1L, products.get(0).getSkuId());
                })
                .verifyComplete();
    }

    @Test
    void vectorFailureFallsBackToKeywordResults() {
        ProductGuideCatalogClient client = mock(ProductGuideCatalogClient.class);
        VectorStore vectorStore = mock(VectorStore.class);
        HybridProductRetrievalService service = new HybridProductRetrievalService(
                client,
                vectorStoreProvider(vectorStore),
                enabledProperties()
        );
        ProductGuideVo keywordProduct = product(1L, "Mac Mini");
        when(client.search(any(ProductGuideQueryDto.class)))
                .thenReturn(Mono.just(List.of(keywordProduct)));
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenThrow(new IllegalStateException("PGvector unavailable"));

        StepVerifier.create(service.search("Mac", 5))
                .assertNext(products -> {
                    assertEquals(1, products.size());
                    assertEquals(1L, products.get(0).getSkuId());
                })
                .verifyComplete();
    }

    @Test
    void disabledVectorModeDoesNotTouchVectorStore() {
        ProductGuideCatalogClient client = mock(ProductGuideCatalogClient.class);
        VectorStore vectorStore = mock(VectorStore.class);
        ProductRetrievalProperties properties = enabledProperties();
        properties.setVectorEnabled(false);
        HybridProductRetrievalService service = new HybridProductRetrievalService(
                client,
                vectorStoreProvider(vectorStore),
                properties
        );
        when(client.search(any(ProductGuideQueryDto.class)))
                .thenReturn(Mono.just(List.of(product(1L, "Mac Mini"))));

        StepVerifier.create(service.search("Mac", 5))
                .expectNextMatches(products -> products.size() == 1)
                .verifyComplete();

        verify(vectorStore, never()).similaritySearch(any(SearchRequest.class));
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<VectorStore> vectorStoreProvider(VectorStore vectorStore) {
        ObjectProvider<VectorStore> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(vectorStore);
        return provider;
    }

    private ProductRetrievalProperties enabledProperties() {
        ProductRetrievalProperties properties = new ProductRetrievalProperties();
        properties.setVectorEnabled(true);
        properties.setSimilarityThreshold(0.5D);
        properties.setVectorCandidateMultiplier(3);
        return properties;
    }

    private ProductGuideVo product(Long skuId, String name) {
        ProductGuideVo product = new ProductGuideVo();
        product.setSkuId(skuId);
        product.setProductName(name);
        product.setInStock(true);
        return product;
    }

    private Document vectorDocument(Long skuId) {
        return Document.builder()
                .id("product-sku-" + skuId)
                .text("product")
                .metadata(Map.of("documentType", "product", "skuId", skuId))
                .build();
    }
}
