package com.tzp.zjzx.product.service;

import com.tzp.zjzx.model.vo.product.CategoryVo;

import java.util.List;


public interface CategoryService {

    /**
     * 查询所有一级分类
     * @return
     */
    List<CategoryVo> selectOneCategory();


    /**
     * 查询所有分类
     * @return
     */
    List<CategoryVo> findCategoryTree();
}
