package com.tzp.zjzx.product.service;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.model.dto.h5.ProductSkuDto;
import com.tzp.zjzx.model.dto.internal.ProductSkuInternalDto;
import com.tzp.zjzx.model.vo.h5.ProductItemVo;
import com.tzp.zjzx.model.vo.product.ProductSkuVo;

import java.util.List;

public interface ProductService {

    PageInfo<ProductSkuVo> findByPage(Integer page, Integer limit, ProductSkuDto productSkuDto);

    ProductItemVo item(Long skuId);

    List<ProductSkuVo> selectProductSkuBySale();

    ProductSkuInternalDto getInternalSku(Long skuId);
}
