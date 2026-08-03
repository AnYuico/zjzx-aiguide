package com.tzp.zjzx.manager.service;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.model.entity.product.Brand;

import java.util.List;

public interface BrandService {
    /**
     * 品牌分页查询
     * @param page
     * @param limit
     * @return
     */
    PageInfo<Brand> findByPage(Integer page, Integer limit);

    /**
     * 品牌添加
     * @param brand
     */
    void save(Brand brand);

    /**
     * 品牌修改
     * @param brand
     */
    void updateById(Brand brand);

    /**
     * 品牌删除
     * @param id
     */
    void deleteById(Long id);

    /**
     * 查询所有品牌
     * @return
     */
    List<Brand> findAll();

}
