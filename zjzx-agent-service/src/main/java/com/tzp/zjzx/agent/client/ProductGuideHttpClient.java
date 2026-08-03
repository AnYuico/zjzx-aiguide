package com.tzp.zjzx.agent.client;

import com.tzp.zjzx.agent.config.ProductGuideProperties;
import com.tzp.zjzx.agent.exception.ProductCatalogUnavailableException;
import com.tzp.zjzx.agent.resilience.AgentResilienceExecutor;
import com.tzp.zjzx.ai.contract.dto.ProductKnowledgePageQueryDto;
import com.tzp.zjzx.ai.contract.dto.ProductGuideQueryDto;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgeDocumentVo;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgePageVo;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeoutException;

@Component
public class ProductGuideHttpClient implements ProductGuideCatalogClient {

    static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";
    private static final String SEARCH_PATH = "/api/product/internal/ai-guide/search";
    private static final String SKU_PATH = "/api/product/internal/ai-guide/sku/{skuId}";
    private static final String KNOWLEDGE_PAGE_PATH =
            "/api/product/internal/ai-guide/knowledge/page";
    private static final String PRODUCT_KNOWLEDGE_PATH =
            "/api/product/internal/ai-guide/knowledge/product/{productId}";

    private final WebClient webClient;
    private final String internalToken;
    private final Duration requestTimeout;
    private final AgentResilienceExecutor resilienceExecutor;

    @Autowired
    public ProductGuideHttpClient(WebClient.Builder webClientBuilder,
                                  ProductGuideProperties properties,
                                  AgentResilienceExecutor resilienceExecutor) {
        this(
                webClientBuilder.baseUrl(properties.getBaseUrl()).build(),
                properties.getInternalToken(),
                properties.getRequestTimeout(),
                resilienceExecutor
        );
    }

    ProductGuideHttpClient(
            WebClient webClient,
            String internalToken,
            Duration requestTimeout,
            AgentResilienceExecutor resilienceExecutor) {
        this.webClient = webClient;
        this.internalToken = internalToken;
        this.requestTimeout = requestTimeout;
        this.resilienceExecutor = resilienceExecutor;
    }

    @Override
    public Mono<List<ProductGuideVo>> search(ProductGuideQueryDto query) {
        return protect(Mono.defer(() -> {
                    verifyConfiguration();
                    return webClient.post()
                            .uri(SEARCH_PATH)
                            .header(INTERNAL_TOKEN_HEADER, internalToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(query)
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, this::upstreamError)
                            .bodyToMono(new ParameterizedTypeReference<List<ProductGuideVo>>() {
                            });
                }))
                .map(products -> products == null
                        ? List.<ProductGuideVo>of()
                        : List.copyOf(products))
                .onErrorMap(this::mapFailure);
    }

    @Override
    public Mono<ProductGuideVo> getBySkuId(Long skuId) {
        return protect(Mono.defer(() -> {
                    verifyConfiguration();
                    return webClient.get()
                            .uri(SKU_PATH, skuId)
                            .header(INTERNAL_TOKEN_HEADER, internalToken)
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, this::upstreamError)
                            .bodyToMono(ProductGuideVo.class);
                }))
                .onErrorMap(this::mapFailure);
    }

    @Override
    public Mono<ProductKnowledgePageVo> getKnowledgePage(ProductKnowledgePageQueryDto query) {
        return protect(Mono.defer(() -> {
                    verifyConfiguration();
                    return webClient.post()
                            .uri(KNOWLEDGE_PAGE_PATH)
                            .header(INTERNAL_TOKEN_HEADER, internalToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .bodyValue(query)
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, this::upstreamError)
                            .bodyToMono(ProductKnowledgePageVo.class);
                }))
                .onErrorMap(this::mapFailure);
    }

    @Override
    public Mono<List<ProductKnowledgeDocumentVo>> getKnowledgeByProductId(
            Long productId) {
        return protect(Mono.defer(() -> {
                    verifyConfiguration();
                    return webClient.get()
                            .uri(PRODUCT_KNOWLEDGE_PATH, productId)
                            .header(INTERNAL_TOKEN_HEADER, internalToken)
                            .retrieve()
                            .onStatus(HttpStatusCode::isError, this::upstreamError)
                            .bodyToMono(new ParameterizedTypeReference<
                                    List<ProductKnowledgeDocumentVo>>() {
                            });
                }))
                .map(documents -> documents == null
                        ? List.<ProductKnowledgeDocumentVo>of()
                        : List.copyOf(documents))
                .onErrorMap(this::mapFailure);
    }

    private <T> Mono<T> protect(Mono<T> operation) {
        return resilienceExecutor.protectProductCatalog(
                operation.timeout(requestTimeout)
        );
    }

    private Mono<? extends Throwable> upstreamError(
            org.springframework.web.reactive.function.client.ClientResponse response) {
        return Mono.error(new ProductCatalogUnavailableException(
                "Product service returned HTTP " + response.statusCode().value()
        ));
    }

    private Throwable mapFailure(Throwable failure) {
        if (failure instanceof ProductCatalogUnavailableException) {
            return failure;
        }
        if (failure instanceof TimeoutException) {
            return new ProductCatalogUnavailableException("Product service request timed out", failure);
        }
        return new ProductCatalogUnavailableException("Product service request failed", failure);
    }

    private void verifyConfiguration() {
        if (!StringUtils.hasText(internalToken)) {
            throw new ProductCatalogUnavailableException("Internal API token is not configured");
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new ProductCatalogUnavailableException("Product service request timeout is invalid");
        }
    }
}
