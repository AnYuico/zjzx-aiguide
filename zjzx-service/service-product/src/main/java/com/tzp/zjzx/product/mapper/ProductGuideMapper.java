package com.tzp.zjzx.product.mapper;

import com.tzp.zjzx.ai.contract.vo.ProductKnowledgeDocumentVo;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductGuideMapper {

    List<ProductGuideVo> search(@Param("keyword") String keyword,
                                @Param("limit") Integer limit);

    ProductGuideVo getBySkuId(@Param("skuId") Long skuId);

    List<ProductKnowledgeDocumentVo> findKnowledgePage(
            @Param("afterSkuId") Long afterSkuId,
            @Param("limit") Integer limit);

    List<ProductKnowledgeDocumentVo> findKnowledgeByProductId(
            @Param("productId") Long productId);
}
