package com.tzp.zjzx.product.mapper;

import com.tzp.zjzx.model.entity.product.ProductDetails;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductDetailsMapper {
    /**
     * 根据商品id查询商品详情
     * @param productId
     * @return
     */
    ProductDetails getByProductId(Long productId);
}
