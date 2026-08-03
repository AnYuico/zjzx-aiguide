package com.tzp.zjzx.manager.mapper;

import com.tzp.zjzx.model.entity.product.ProductDetails;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ProductDetailsMapper {
    /**
     * 添加详情
     * @param productDetails
     */
    void save(ProductDetails productDetails);

    /**
     * 根据商品id查询详情信息
     * @param id
     * @return
     */
    ProductDetails findProductDetailsById(Long id);


    /**
     * 根据id修改详情
     * @param productDetails
     */
    void updateById(ProductDetails productDetails);

    /**
     * 根据id删除详情
     * @param id
     */
    void deleteByProductId(Long id);
}
