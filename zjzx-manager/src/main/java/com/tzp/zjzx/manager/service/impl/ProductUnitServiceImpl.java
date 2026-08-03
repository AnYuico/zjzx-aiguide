package com.tzp.zjzx.manager.service.impl;

import com.tzp.zjzx.manager.mapper.ProductUnitMapper;
import com.tzp.zjzx.manager.service.ProductUnitService;
import com.tzp.zjzx.model.entity.base.ProductUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductUnitServiceImpl implements ProductUnitService {

    @Autowired
    private ProductUnitMapper productUnitMapper;

    /**
     * 查询所有
     * @return
     */
    @Override
    public List<ProductUnit> findAll() {
        return productUnitMapper.findAll();
    }
}
