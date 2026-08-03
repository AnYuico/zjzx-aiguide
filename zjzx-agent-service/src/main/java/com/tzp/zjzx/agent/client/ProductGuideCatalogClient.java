package com.tzp.zjzx.agent.client;

import com.tzp.zjzx.ai.contract.dto.ProductKnowledgePageQueryDto;
import com.tzp.zjzx.ai.contract.dto.ProductGuideQueryDto;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgeDocumentVo;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgePageVo;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import reactor.core.publisher.Mono;

import java.util.List;

public interface ProductGuideCatalogClient {

    Mono<List<ProductGuideVo>> search(ProductGuideQueryDto query);

    Mono<ProductGuideVo> getBySkuId(Long skuId);

    Mono<ProductKnowledgePageVo> getKnowledgePage(ProductKnowledgePageQueryDto query);

    Mono<List<ProductKnowledgeDocumentVo>> getKnowledgeByProductId(Long productId);
}
