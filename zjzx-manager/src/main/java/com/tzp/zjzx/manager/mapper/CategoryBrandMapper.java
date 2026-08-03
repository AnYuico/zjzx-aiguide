package com.tzp.zjzx.manager.mapper;

import com.tzp.zjzx.model.dto.product.CategoryBrandDto;
import com.tzp.zjzx.model.entity.product.Brand;
import com.tzp.zjzx.model.entity.product.CategoryBrand;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CategoryBrandMapper {
    /**
     * 分类品牌分页查询
     * @param categoryBrandDto
     * @return
     */
    List<CategoryBrand> findByPage(CategoryBrandDto categoryBrandDto);

    /**
     * 保存分类品牌
     * @param categoryBrand
     */
    void save(CategoryBrand categoryBrand);

    /**
     * 修改分类品牌
     * @param categoryBrand
     */
    void updateById(CategoryBrand categoryBrand);

    /**
     * 删除分类品牌
     * @param id
     */
    void deleteById(Long id);

    /**
     * 根据分类id查询对应品牌数据
     * @param categoryId
     * @return
     */
    List<Brand> findBrandByCategoryId(Long categoryId);
}
