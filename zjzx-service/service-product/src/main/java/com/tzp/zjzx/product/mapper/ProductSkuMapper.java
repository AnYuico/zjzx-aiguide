package com.tzp.zjzx.product.mapper;

import com.tzp.zjzx.model.dto.h5.ProductSkuDto;
import com.tzp.zjzx.model.entity.product.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductSkuMapper {
    /**
     * 查询销量前十的数据
     * @return
     */
    List<ProductSku> selectProductSkuBySale();

    /**
     * 分页查询
     * @param productSkuDto
     * @return
     */
    List<ProductSku> findByPage(ProductSkuDto productSkuDto);

    /**
     * 根据id查询sku信息
     * @param skuId
     * @return
     */
    ProductSku getById(Long skuId);

    /**
     * 根据商品id查询商品所有sku列表
     * @param productId
     * @return
     */
    List<ProductSku> findByProductId(Long productId);

    int reserveStock(@Param("skuId") Long skuId, @Param("skuNum") Integer skuNum);

    int restoreStock(@Param("skuId") Long skuId, @Param("skuNum") Integer skuNum);

    int increaseSale(@Param("skuId") Long skuId, @Param("skuNum") Integer skuNum);
}
