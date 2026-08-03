package com.tzp.zjzx.product.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.entity.product.Product;
import com.tzp.zjzx.model.entity.product.ProductDetails;
import com.tzp.zjzx.model.entity.product.ProductSku;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.h5.ProductItemVo;
import com.tzp.zjzx.product.mapper.ProductDetailsMapper;
import com.tzp.zjzx.product.mapper.ProductMapper;
import com.tzp.zjzx.product.mapper.ProductSkuMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductSkuMapper productSkuMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductDetailsMapper productDetailsMapper;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(productSkuMapper, productMapper, productDetailsMapper);
    }

    @Test
    void itemRejectsInactiveSkuBeforeLoadingProduct() {
        ProductSku sku = new ProductSku();
        sku.setId(14L);
        sku.setStatus(0);
        when(productSkuMapper.getById(14L)).thenReturn(sku);

        MyException exception = assertThrows(MyException.class, () -> productService.item(14L));

        assertEquals(ResultCodeEnum.DATA_ERROR, exception.getResultCodeEnum());
        verifyNoInteractions(productMapper, productDetailsMapper);
    }

    @Test
    void itemReturnsOnlyNonBlankImageUrls() {
        ProductSku sku = new ProductSku();
        sku.setId(14L);
        sku.setProductId(7L);
        sku.setSkuSpec("{} ");
        sku.setStatus(1);

        Product product = new Product();
        product.setId(7L);
        product.setStatus(1);
        product.setAuditStatus(1);
        product.setSliderUrls(" , http://example.test/slider.png, ");
        product.setSpecValue("[]");

        ProductDetails details = new ProductDetails();
        details.setProductId(7L);
        details.setImageUrls("");

        when(productSkuMapper.getById(14L)).thenReturn(sku);
        when(productMapper.getById(7L)).thenReturn(product);
        when(productDetailsMapper.getByProductId(7L)).thenReturn(details);
        when(productSkuMapper.findByProductId(7L)).thenReturn(Collections.singletonList(sku));

        ProductItemVo result = productService.item(14L);

        assertEquals(Collections.singletonList("http://example.test/slider.png"),
                result.getSliderUrlList());
        assertEquals(Collections.emptyList(), result.getDetailsImageUrlList());
    }
}
