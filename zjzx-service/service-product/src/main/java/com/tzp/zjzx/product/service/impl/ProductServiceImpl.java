package com.tzp.zjzx.product.service.impl;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.dto.h5.ProductSkuDto;
import com.tzp.zjzx.model.dto.internal.ProductSkuInternalDto;
import com.tzp.zjzx.model.entity.product.Product;
import com.tzp.zjzx.model.entity.product.ProductDetails;
import com.tzp.zjzx.model.entity.product.ProductSku;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.h5.ProductItemVo;
import com.tzp.zjzx.model.vo.product.ProductInfoVo;
import com.tzp.zjzx.model.vo.product.ProductSkuVo;
import com.tzp.zjzx.product.mapper.ProductDetailsMapper;
import com.tzp.zjzx.product.mapper.ProductMapper;
import com.tzp.zjzx.product.mapper.ProductSkuMapper;
import com.tzp.zjzx.product.service.ProductService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ProductServiceImpl implements ProductService {

    private final ProductSkuMapper productSkuMapper;
    private final ProductMapper productMapper;
    private final ProductDetailsMapper productDetailsMapper;

    public ProductServiceImpl(ProductSkuMapper productSkuMapper,
                              ProductMapper productMapper,
                              ProductDetailsMapper productDetailsMapper) {
        this.productSkuMapper = productSkuMapper;
        this.productMapper = productMapper;
        this.productDetailsMapper = productDetailsMapper;
    }

    @Override
    public List<ProductSkuVo> selectProductSkuBySale() {
        return productSkuMapper.selectProductSkuBySale().stream()
                .map(this::toSkuVo)
                .collect(Collectors.toList());
    }

    @Override
    public ProductSkuInternalDto getInternalSku(Long skuId) {
        ProductSku productSku = productSkuMapper.getById(skuId);
        if (productSku == null) {
            return null;
        }
        ProductSkuInternalDto internalDto = new ProductSkuInternalDto();
        BeanUtils.copyProperties(productSku, internalDto);
        return internalDto;
    }

    @Override
    public PageInfo<ProductSkuVo> findByPage(Integer page,
                                             Integer limit,
                                             ProductSkuDto productSkuDto) {
        PageHelper.startPage(page, limit);
        List<ProductSku> productSkus = productSkuMapper.findByPage(productSkuDto);
        PageInfo<ProductSku> entityPage = new PageInfo<>(productSkus);
        PageInfo<ProductSkuVo> resultPage = new PageInfo<>();
        BeanUtils.copyProperties(entityPage, resultPage, "list");
        resultPage.setList(productSkus.stream().map(this::toSkuVo).collect(Collectors.toList()));
        return resultPage;
    }

    @Override
    public ProductItemVo item(Long skuId) {
        ProductSku productSku = productSkuMapper.getById(skuId);
        if (productSku == null || !Integer.valueOf(1).equals(productSku.getStatus())) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        Product product = productMapper.getById(productSku.getProductId());
        ProductDetails productDetails = productDetailsMapper.getByProductId(productSku.getProductId());
        if (product == null
                || !Integer.valueOf(1).equals(product.getStatus())
                || !Integer.valueOf(1).equals(product.getAuditStatus())
                || productDetails == null) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }

        Map<String, Object> skuSpecValueMap = new HashMap<>();
        productSkuMapper.findByProductId(productSku.getProductId()).forEach(sku ->
                skuSpecValueMap.put(sku.getSkuSpec(), sku.getId())
        );

        ProductInfoVo productInfoVo = new ProductInfoVo();
        BeanUtils.copyProperties(product, productInfoVo);
        ProductItemVo productItemVo = new ProductItemVo();
        productItemVo.setProduct(productInfoVo);
        productItemVo.setProductSku(toSkuVo(productSku));
        productItemVo.setSkuSpecValueMap(skuSpecValueMap);
        productItemVo.setDetailsImageUrlList(parseImageUrls(productDetails.getImageUrls()));
        productItemVo.setSliderUrlList(parseImageUrls(product.getSliderUrls()));
        productItemVo.setSpecValueList(JSON.parseArray(product.getSpecValue()));
        return productItemVo;
    }

    private ProductSkuVo toSkuVo(ProductSku productSku) {
        ProductSkuVo skuVo = new ProductSkuVo();
        BeanUtils.copyProperties(productSku, skuVo);
        return skuVo;
    }

    private List<String> parseImageUrls(String imageUrls) {
        if (!StringUtils.hasText(imageUrls)) {
            return Collections.emptyList();
        }
        return java.util.Arrays.stream(imageUrls.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .collect(Collectors.toList());
    }
}
