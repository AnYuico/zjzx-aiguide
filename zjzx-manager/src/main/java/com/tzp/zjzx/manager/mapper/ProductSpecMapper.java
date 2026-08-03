package com.tzp.zjzx.manager.mapper;

import com.tzp.zjzx.model.entity.product.ProductSpec;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductSpecMapper {
    /**
     * 分页查询商品规格 查询全部商品规格
     * @return
     */
    List<ProductSpec> findByPage();

    /**
     * 保存商品规格
     * @param productSpec
     */
    void save(ProductSpec productSpec);

    /**
     * 修改商品规格
     * @param productSpec
     */
    void updateById(ProductSpec productSpec);

    /**
     * 删除商品规格
     * @param id
     */
    void deleteById(Long id);

    /**
     * 查询商品的所有规格
     * @return
     */
    List<ProductSpec> findAll();
}
