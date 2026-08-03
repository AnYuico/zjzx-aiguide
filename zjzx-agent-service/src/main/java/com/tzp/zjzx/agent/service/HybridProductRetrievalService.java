package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.agent.client.ProductGuideCatalogClient;
import com.tzp.zjzx.agent.config.ProductRetrievalProperties;
import com.tzp.zjzx.ai.contract.dto.ProductGuideQueryDto;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class HybridProductRetrievalService {

    private static final Logger LOGGER =
            LoggerFactory.getLogger(HybridProductRetrievalService.class);
    private static final int RRF_RANK_CONSTANT = 60;
    private static final int MAX_VECTOR_CANDIDATES = 100;

    private final ProductGuideCatalogClient productGuideCatalogClient;
    private final ObjectProvider<VectorStore> vectorStoreProvider;
    private final ProductRetrievalProperties properties;

    public HybridProductRetrievalService(
            ProductGuideCatalogClient productGuideCatalogClient,
            ObjectProvider<VectorStore> vectorStoreProvider,
            ProductRetrievalProperties properties) {
        this.productGuideCatalogClient = productGuideCatalogClient;
        this.vectorStoreProvider = vectorStoreProvider;
        this.properties = properties;
    }

    public Mono<List<ProductGuideVo>> search(String keyword, int limit) {
        ProductGuideQueryDto query = new ProductGuideQueryDto();
        query.setKeyword(keyword);
        query.setLimit(limit);

        return productGuideCatalogClient.search(query)
                .flatMap(keywordProducts -> {
                    List<ProductGuideVo> currentProducts =
                            keywordProducts == null ? List.of() : keywordProducts;
                    if (!canUseVectorSearch(keyword)) {
                        return Mono.just(List.copyOf(currentProducts));
                    }
                    return vectorSearch(keyword, limit)
                            .flatMap(vectorDocuments -> mergeWithRealtimeValidation(
                                    currentProducts,
                                    vectorDocuments,
                                    limit
                            ))
                            .timeout(properties.getHybridTimeout())
                            .onErrorResume(failure -> {
                                LOGGER.warn(
                                        "Vector retrieval failed; using keyword results: {}",
                                        failure.getClass().getSimpleName()
                                );
                                return Mono.just(List.copyOf(currentProducts));
                            });
                });
    }

    private boolean canUseVectorSearch(String keyword) {
        return properties.isVectorEnabled()
                && StringUtils.hasText(keyword)
                && properties.getHybridTimeout() != null
                && !properties.getHybridTimeout().isZero()
                && !properties.getHybridTimeout().isNegative()
                && vectorStoreProvider.getIfAvailable() != null;
    }

    private Mono<List<Document>> vectorSearch(String keyword, int limit) {
        return Mono.fromCallable(() -> {
                    VectorStore vectorStore = vectorStoreProvider.getIfAvailable();
                    if (vectorStore == null) {
                        return List.<Document>of();
                    }
                    int multiplier = Math.max(
                            1,
                            properties.getVectorCandidateMultiplier()
                    );
                    int topK = Math.min(
                            MAX_VECTOR_CANDIDATES,
                            Math.max(limit, limit * multiplier)
                    );
                    SearchRequest request = SearchRequest.builder()
                            .query(keyword)
                            .topK(topK)
                            .similarityThreshold(
                                    properties.getSimilarityThreshold()
                            )
                            .filterExpression("documentType == 'product'")
                            .build();
                    List<Document> documents =
                            vectorStore.similaritySearch(request);
                    return documents == null ? List.<Document>of() : documents;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Mono<List<ProductGuideVo>> mergeWithRealtimeValidation(
            List<ProductGuideVo> keywordProducts,
            List<Document> vectorDocuments,
            int limit) {
        Map<Long, ProductGuideVo> currentBySku = new LinkedHashMap<>();
        for (ProductGuideVo product : keywordProducts) {
            if (isValidProduct(product)) {
                currentBySku.putIfAbsent(product.getSkuId(), product);
            }
        }

        List<VectorCandidate> candidates = vectorCandidates(vectorDocuments);
        return Flux.fromIterable(candidates)
                .flatMapSequential(candidate -> {
                    ProductGuideVo current = currentBySku.get(candidate.skuId());
                    if (current != null) {
                        return Mono.just(new ValidatedCandidate(
                                candidate.rank(),
                                current
                        ));
                    }
                    return productGuideCatalogClient.getBySkuId(candidate.skuId())
                            .filter(this::isValidProduct)
                            .map(product -> new ValidatedCandidate(
                                    candidate.rank(),
                                    product
                            ))
                            .onErrorResume(failure -> {
                                LOGGER.debug(
                                        "Discarding stale vector candidate SKU {}",
                                        candidate.skuId()
                                );
                                return Mono.empty();
                            });
                }, 4, 1)
                .collectList()
                .map(validated -> rankResults(
                        keywordProducts,
                        validated,
                        limit
                ));
    }

    private List<VectorCandidate> vectorCandidates(List<Document> documents) {
        List<VectorCandidate> candidates = new ArrayList<>();
        Set<Long> seenSkuIds = new LinkedHashSet<>();
        int rank = 1;
        for (Document document : documents) {
            Long skuId = metadataLong(document, "skuId");
            if (skuId != null && skuId > 0 && seenSkuIds.add(skuId)) {
                candidates.add(new VectorCandidate(rank, skuId));
            }
            rank++;
        }
        return candidates;
    }

    private List<ProductGuideVo> rankResults(
            List<ProductGuideVo> keywordProducts,
            List<ValidatedCandidate> vectorProducts,
            int limit) {
        Map<Long, RankedProduct> ranked = new HashMap<>();
        int keywordRank = 1;
        for (ProductGuideVo product : keywordProducts) {
            if (isValidProduct(product)) {
                addScore(ranked, product, reciprocalRank(keywordRank));
            }
            keywordRank++;
        }
        for (ValidatedCandidate candidate : vectorProducts) {
            addScore(
                    ranked,
                    candidate.product(),
                    reciprocalRank(candidate.rank())
            );
        }

        return ranked.values().stream()
                .sorted(Comparator
                        .comparingDouble(RankedProduct::score)
                        .reversed()
                        .thenComparing(value -> value.product().getSkuId()))
                .limit(limit)
                .map(RankedProduct::product)
                .toList();
    }

    private void addScore(Map<Long, RankedProduct> ranked,
                          ProductGuideVo product,
                          double score) {
        ranked.merge(
                product.getSkuId(),
                new RankedProduct(product, score),
                (existing, added) -> new RankedProduct(
                        existing.product(),
                        existing.score() + added.score()
                )
        );
    }

    private double reciprocalRank(int rank) {
        return 1D / (RRF_RANK_CONSTANT + rank);
    }

    private Long metadataLong(Document document, String key) {
        Object value = document.getMetadata().get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text) {
            try {
                return Long.valueOf(text);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private boolean isValidProduct(ProductGuideVo product) {
        return product != null
                && product.getSkuId() != null
                && product.getSkuId() > 0;
    }

    private record VectorCandidate(int rank, Long skuId) {
    }

    private record ValidatedCandidate(int rank, ProductGuideVo product) {
    }

    private record RankedProduct(ProductGuideVo product, double score) {
    }
}
