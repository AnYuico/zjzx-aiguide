package com.tzp.zjzx.product.service;

import com.tzp.zjzx.model.vo.product.BrandVo;

import java.util.List;

public interface BrandService {
    /**
     * 获取所有品牌
     * @return
     */
    List<BrandVo> findAll();
}
