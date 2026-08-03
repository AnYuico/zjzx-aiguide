package com.tzp.zjzx.product.mapper;

import com.tzp.zjzx.model.entity.product.Brand;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BrandMapper {
    /**
     * 获取所有品牌
     * @return
     */
    List<Brand> findAll();
}
