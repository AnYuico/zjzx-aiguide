package com.tzp.zjzx.product.mapper;

import com.tzp.zjzx.model.entity.product.Category;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CategoryMapper {
    /**
     * 查询所有一级分类
     * @return
     */
    List<Category> selectOneCategory();

    /**
     * 查询所有分类
     * @return
     */
    List<Category> findAll();
}
