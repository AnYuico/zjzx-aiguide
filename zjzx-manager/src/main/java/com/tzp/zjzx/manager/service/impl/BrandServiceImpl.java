package com.tzp.zjzx.manager.service.impl;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.manager.mapper.BrandMapper;
import com.tzp.zjzx.manager.service.BrandService;
import com.tzp.zjzx.model.entity.product.Brand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class BrandServiceImpl implements BrandService {


    @Autowired
    private BrandMapper brandMapper;

    /**
     * 品牌分页查询
     * @param page
     * @param limit
     * @return
     */
    @Override
    public PageInfo<Brand> findByPage(Integer page, Integer limit) {
        PageHelper.startPage(page,limit);
        List<Brand> list =  brandMapper.findByPage();
        PageInfo<Brand> pageInfo = new PageInfo<>(list);
        return pageInfo;
    }

    /**
     * 品牌添加
     * @param brand
     */
    @Override
    public void save(Brand brand) {
        brandMapper.save(brand);
    }

    /**
     * 品牌修改
     * @param brand
     */
    @Override
    public void updateById(Brand brand) {
        brandMapper.updateById(brand);
    }

    /**
     * 品牌删除
     * @param id
     */
    @Override
    public void deleteById(Long id) {
        brandMapper.deleteById(id);
    }

    /**
     * 查询所有品牌
     * @return
     */
    @Override
    public List<Brand> findAll() {
        return brandMapper.findByPage();
    }
}
