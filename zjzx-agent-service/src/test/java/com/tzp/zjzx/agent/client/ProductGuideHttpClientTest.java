package com.tzp.zjzx.agent.client;

import com.tzp.zjzx.agent.exception.ProductCatalogUnavailableException;
import com.tzp.zjzx.agent.resilience.AgentResilienceExecutor;
import com.tzp.zjzx.ai.contract.dto.ProductKnowledgePageQueryDto;
import com.tzp.zjzx.ai.contract.dto.ProductGuideQueryDto;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductGuideHttpClientTest {

    @Test
    void injectsInternalTokenAndDecodesAllowlistedProducts() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            capturedRequest.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            [{
                              "skuId": 14,
                              "productName": "Mac mini",
                              "skuName": "16G",
                              "salePrice": 1999.00,
                              "inStock": true
                            }]
                            """)
                    .build());
        };
        ProductGuideHttpClient client = client(exchangeFunction, "test-internal-token");
        ProductGuideQueryDto query = new ProductGuideQueryDto();
        query.setKeyword("Mac");
        query.setLimit(5);

        StepVerifier.create(client.search(query))
                .assertNext(products -> {
                    assertEquals(1, products.size());
                    assertEquals(14L, products.get(0).getSkuId());
                    assertEquals("Mac mini", products.get(0).getProductName());
                })
                .verifyComplete();

        assertEquals("/api/product/internal/ai-guide/search",
                capturedRequest.get().url().getPath());
        assertEquals("test-internal-token",
                capturedRequest.get().headers().getFirst(ProductGuideHttpClient.INTERNAL_TOKEN_HEADER));
    }

    @Test
    void convertsUpstreamFailureToStableCatalogException() {
        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.FORBIDDEN).build()
        );
        ProductGuideHttpClient client = client(exchangeFunction, "test-internal-token");

        StepVerifier.create(client.search(new ProductGuideQueryDto()))
                .expectError(ProductCatalogUnavailableException.class)
                .verify();
    }

    @Test
    void decodesKnowledgePageForIndexing() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            capturedRequest.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            {
                              "items": [{
                                "productId": 10,
                                "skuId": 14,
                                "brandName": "Apple",
                                "categoryPath": "数码 > 电脑",
                                "contentHash": "abc"
                              }],
                              "nextCursor": 14,
                              "hasMore": false
                            }
                            """)
                    .build());
        };
        ProductGuideHttpClient client = client(exchangeFunction, "test-internal-token");
        ProductKnowledgePageQueryDto query = new ProductKnowledgePageQueryDto();
        query.setAfterSkuId(0L);
        query.setLimit(100);

        StepVerifier.create(client.getKnowledgePage(query))
                .assertNext(page -> {
                    assertEquals(1, page.getItems().size());
                    assertEquals(14L, page.getItems().get(0).getSkuId());
                    assertEquals(14L, page.getNextCursor());
                })
                .verifyComplete();

        assertEquals("/api/product/internal/ai-guide/knowledge/page",
                capturedRequest.get().url().getPath());
    }

    @Test
    void rejectsMissingInternalTokenWithoutSendingRequest() {
        ExchangeFunction exchangeFunction = request -> Mono.error(
                new AssertionError("HTTP request must not be sent")
        );
        ProductGuideHttpClient client = client(exchangeFunction, " ");

        StepVerifier.create(client.search(new ProductGuideQueryDto()))
                .expectError(ProductCatalogUnavailableException.class)
                .verify();
    }

    @Test
    void loadsCurrentKnowledgeSnapshotByProductId() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        ExchangeFunction exchangeFunction = request -> {
            capturedRequest.set(request);
            return Mono.just(ClientResponse.create(HttpStatus.OK)
                    .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .body("""
                            [{
                              "productId": 10,
                              "skuId": 14,
                              "productName": "Mac mini",
                              "contentHash": "abc"
                            }]
                            """)
                    .build());
        };
        ProductGuideHttpClient client =
                client(exchangeFunction, "test-internal-token");

        StepVerifier.create(client.getKnowledgeByProductId(10L))
                .assertNext(documents -> {
                    assertEquals(1, documents.size());
                    assertEquals(14L, documents.get(0).getSkuId());
                })
                .verifyComplete();

        assertEquals(
                "/api/product/internal/ai-guide/knowledge/product/10",
                capturedRequest.get().url().getPath()
        );
    }

    private ProductGuideHttpClient client(ExchangeFunction exchangeFunction, String token) {
        WebClient webClient = WebClient.builder()
                .baseUrl("http://127.0.0.1:8511")
                .exchangeFunction(exchangeFunction)
                .build();
        AgentResilienceExecutor resilienceExecutor =
                mock(AgentResilienceExecutor.class);
        when(resilienceExecutor.protectProductCatalog(any()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        return new ProductGuideHttpClient(
                webClient,
                token,
                Duration.ofSeconds(1),
                resilienceExecutor
        );
    }
}
