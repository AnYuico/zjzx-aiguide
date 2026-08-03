package com.tzp.zjzx.manager.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.manager.mapper.ProductDetailsMapper;
import com.tzp.zjzx.manager.mapper.ProductMapper;
import com.tzp.zjzx.manager.mapper.ProductSkuMapper;
import com.tzp.zjzx.manager.service.ProductKnowledgeOutboxService;
import com.tzp.zjzx.model.dto.product.ProductCreateDto;
import com.tzp.zjzx.model.dto.product.ProductSkuCreateDto;
import com.tzp.zjzx.model.entity.product.ProductDetails;
import com.tzp.zjzx.model.entity.product.ProductSku;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductSkuMapper productSkuMapper;

    @Mock
    private ProductDetailsMapper productDetailsMapper;

    @Mock
    private ProductKnowledgeOutboxService productKnowledgeOutboxService;

    private ProductServiceImpl productService;

    @BeforeEach
    void setUp() {
        productService = new ProductServiceImpl(
                productMapper,
                productSkuMapper,
                productDetailsMapper,
                productKnowledgeOutboxService
        );
    }

    @Test
    void saveWithoutSkuRejectsBeforeDatabaseWrites() {
        ProductCreateDto product = new ProductCreateDto();
        product.setName("Mac mini");

        MyException exception = assertThrows(MyException.class,
                () -> productService.save(product));

        assertEquals(ResultCodeEnum.PRODUCT_SKU_REQUIRED,
                exception.getResultCodeEnum());
        verifyNoInteractions(productMapper, productSkuMapper, productDetailsMapper);
    }

    @Test
    void savePersistsProductSkuAndDetails() {
        ProductCreateDto product = new ProductCreateDto();
        product.setName("Mac mini");
        product.setDetailsImageUrls("http://example.test/detail.jpg");
        ProductSkuCreateDto productSku = new ProductSkuCreateDto();
        productSku.setSkuSpec("16GB + 256GB");
        productSku.setWeight(new BigDecimal("0.50"));
        productSku.setVolume(new BigDecimal("0.02"));
        product.setProductSkuList(Collections.singletonList(productSku));
        doAnswer(invocation -> {
            ((com.tzp.zjzx.model.entity.product.Product) invocation.getArgument(0)).setId(10L);
            return null;
        }).when(productMapper).save(any(com.tzp.zjzx.model.entity.product.Product.class));

        productService.save(product);

        verify(productMapper).save(argThat(saved ->
                Integer.valueOf(0).equals(saved.getStatus())
                        && Integer.valueOf(0).equals(saved.getAuditStatus())));
        verify(productSkuMapper).save(argThat(saved ->
                Long.valueOf(10L).equals(saved.getProductId())
                        && "10_0".equals(saved.getSkuCode())
                        && "Mac mini16GB + 256GB".equals(saved.getSkuName())
                        && Integer.valueOf(0).equals(saved.getSaleNum())
                        && Integer.valueOf(0).equals(saved.getStatus())
                        && new BigDecimal("0.50").equals(saved.getWeight())
                        && new BigDecimal("0.02").equals(saved.getVolume())));
        verify(productDetailsMapper).save(argThat((ProductDetails details) ->
                Long.valueOf(10L).equals(details.getProductId())
                        && "http://example.test/detail.jpg".equals(details.getImageUrls())));
        verify(productKnowledgeOutboxService).enqueue(
                10L,
                ProductKnowledgeOutboxService.CREATED
        );
    }

    @Test
    void updateStatusSynchronizesProductAndSkuStatus() {
        productService.updateStatus(10L, 1);

        verify(productMapper).updateById(argThat(product ->
                Long.valueOf(10L).equals(product.getId())
                        && Integer.valueOf(1).equals(product.getStatus())));
        verify(productSkuMapper).updateStatusByProductId(10L, 1);
        verify(productKnowledgeOutboxService).enqueue(
                10L,
                ProductKnowledgeOutboxService.STATUS_CHANGED
        );
    }
}
