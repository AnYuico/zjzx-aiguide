package com.tzp.zjzx.manager.mapper;

import com.tzp.zjzx.model.entity.product.ProductSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ProductSkuMapper {
    /**
     * 添加sku
     * @param productSku
     */
    void save(ProductSku productSku);

    /**
     * 根据商品id查询sku
     * @param id
     * @return
     */
    List<ProductSku> findByProductSkuById(Long id);

    /**
     * 根据id修改sku
     * @param productSku
     */
    void updateById(ProductSku productSku);

    void updateStatusByProductId(@Param("productId") Long productId,
                                 @Param("status") Integer status);

    /**
     * 根据id删除sku
     * @param id
     */
    void deleteByProductId(Long id);
}
