package com.tzp.zjzx.product.service;

import com.tzp.zjzx.ai.contract.dto.ProductKnowledgePageQueryDto;
import com.tzp.zjzx.ai.contract.dto.ProductGuideQueryDto;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgeDocumentVo;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgePageVo;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;

import java.util.List;

public interface ProductGuideService {

    List<ProductGuideVo> search(ProductGuideQueryDto query);

    ProductGuideVo getBySkuId(Long skuId);

    ProductKnowledgePageVo getKnowledgePage(ProductKnowledgePageQueryDto query);

    List<ProductKnowledgeDocumentVo> getKnowledgeByProductId(Long productId);
}
