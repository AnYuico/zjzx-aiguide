package com.tzp.zjzx.manager.service;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.model.entity.product.ProductSpec;

import java.util.List;

public interface ProductSpecService {

    /**
     * 分页查询
     * @param page
     * @param limit
     * @return
     */
    PageInfo<ProductSpec> findByPage(Integer page, Integer limit);

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
