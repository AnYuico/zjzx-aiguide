package com.tzp.zjzx.product.service.impl;

import com.tzp.zjzx.model.entity.product.Brand;
import com.tzp.zjzx.model.vo.product.BrandVo;
import com.tzp.zjzx.product.mapper.BrandMapper;
import com.tzp.zjzx.product.service.BrandService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class BrandServiceImpl implements BrandService {

    @Autowired
    private BrandMapper brandMapper;

    /**
     * 获取所有品牌
     *
     * @return
     */
    @Override
    public List<BrandVo> findAll() {
        return brandMapper.findAll().stream().map(this::toBrandVo).collect(Collectors.toList());
    }

    private BrandVo toBrandVo(Brand brand) {
        BrandVo result = new BrandVo();
        BeanUtils.copyProperties(brand, result);
        return result;
    }
}
