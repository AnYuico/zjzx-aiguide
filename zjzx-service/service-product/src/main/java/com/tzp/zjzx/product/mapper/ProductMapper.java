package com.tzp.zjzx.product.mapper;

import com.tzp.zjzx.model.entity.product.Product;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductMapper {
    /**
     * 根据id查询
     * @param productId
     * @return
     */
    Product getById(Long productId);
}
