package com.tzp.zjzx.manager.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.manager.mapper.ProductSpecMapper;
import com.tzp.zjzx.manager.service.ProductSpecService;
import com.tzp.zjzx.model.entity.product.ProductSpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductSpecServiceImpl implements ProductSpecService {

    @Autowired
    private ProductSpecMapper productSpecMapper;


    /**
     * 分页查询
     * @param page
     * @param limit
     * @return
     */
    @Override
    public PageInfo<ProductSpec> findByPage(Integer page, Integer limit) {
        PageHelper.startPage(page, limit);
        List<ProductSpec> list = productSpecMapper.findByPage();
        PageInfo<ProductSpec> pageInfo = new PageInfo<>(list);
        return pageInfo;
    }

    /**
     * 保存商品规格
     * @param productSpec
     */
    @Override
    public void save(ProductSpec productSpec) {
        productSpecMapper.save(productSpec);
    }

    /**
     * 修改商品规格
     * @param productSpec
     */
    @Override
    public void updateById(ProductSpec productSpec) {
        productSpecMapper.updateById(productSpec);
    }

    /**
     * 删除商品规格
     * @param id
     */
    @Override
    public void deleteById(Long id) {
        productSpecMapper.deleteById(id);
    }

    /**
     * 查询商品的所有规格
     * @return
     */
    @Override
    public List<ProductSpec> findAll() {
        return productSpecMapper.findAll();
    }
}
