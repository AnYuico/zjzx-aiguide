package com.tzp.zjzx.manager.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.manager.mapper.CategoryBrandMapper;
import com.tzp.zjzx.manager.service.CategoryBrandService;
import com.tzp.zjzx.model.dto.product.CategoryBrandDto;
import com.tzp.zjzx.model.entity.product.Brand;
import com.tzp.zjzx.model.entity.product.CategoryBrand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryBrandServiceImpl implements CategoryBrandService {

    @Autowired
    private CategoryBrandMapper categoryBrandMapper;
    /**
     * 1.分类品牌分页查询
     * @param page
     * @param limit
     * @param categoryBrandDto
     * @return
     */
    @Override
    public PageInfo<CategoryBrand> findByPage(Integer page, Integer limit, CategoryBrandDto categoryBrandDto) {
        PageHelper.startPage(page, limit);
        List<CategoryBrand> list = categoryBrandMapper.findByPage(categoryBrandDto);
        PageInfo<CategoryBrand> pageInfo = new PageInfo<>(list);
        return pageInfo;
    }

    /**
     * 2.保存分类品牌
     * @param categoryBrand
     */
    @Override
    public void save(CategoryBrand categoryBrand) {
        categoryBrandMapper.save(categoryBrand);
    }

    /**
     * 3.修改分类品牌
     * @param categoryBrand
     */
    @Override
    public void updateById(CategoryBrand categoryBrand) {
        categoryBrandMapper.updateById(categoryBrand);
    }

    /**
     * 4.删除分类品牌
     * @param id
     */
    @Override
    public void deleteById(Long id) {
        categoryBrandMapper.deleteById(id);
    }

    /**
     * 5.根据分类id查询对应品牌数据
     * @param categoryId
     * @return
     */
    @Override
    public List<Brand> findBrandByCategoryId(Long categoryId) {
        return categoryBrandMapper.findBrandByCategoryId(categoryId);
    }
}
