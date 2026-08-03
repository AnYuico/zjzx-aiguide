package com.tzp.zjzx.manager.mapper;

import com.tzp.zjzx.model.entity.product.Brand;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface BrandMapper {

    /**
     * 品牌分页查询
     * @return
     */
    List<Brand> findByPage();

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


}
