package com.tzp.zjzx.product.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.dto.seckill.SeckillActivityCreateDto;
import com.tzp.zjzx.model.dto.seckill.SeckillSkuCreateDto;
import com.tzp.zjzx.model.entity.product.ProductSku;
import com.tzp.zjzx.model.entity.seckill.SeckillActivity;
import com.tzp.zjzx.model.entity.seckill.SeckillSku;
import com.tzp.zjzx.model.enums.SeckillActivityStatus;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.product.mapper.ProductSkuMapper;
import com.tzp.zjzx.product.mapper.SeckillActivityMapper;
import com.tzp.zjzx.product.mapper.SeckillSkuMapper;
import com.tzp.zjzx.product.service.SeckillActivityLifecycleService;
import com.tzp.zjzx.product.service.SeckillRedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillActivityServiceImplTest {

    @Mock
    private SeckillActivityMapper activityMapper;
    @Mock
    private SeckillSkuMapper seckillSkuMapper;
    @Mock
    private ProductSkuMapper productSkuMapper;
    @Mock
    private SeckillActivityLifecycleService lifecycleService;
    @Mock
    private SeckillRedisService redisService;

    private SeckillActivityServiceImpl activityService;

    @BeforeEach
    void setUp() {
        activityService = new SeckillActivityServiceImpl(
                activityMapper,
                seckillSkuMapper,
                productSkuMapper,
                lifecycleService,
                redisService);
    }

    @Test
    void updateReplacesSkuConfigurationOnlyWhileActivityIsDraft() {
        SeckillActivityCreateDto dto = validDto();
        when(activityMapper.updateDraft(
                10L, dto.getName(), dto.getStartTime(), dto.getEndTime()))
                .thenReturn(1);
        when(productSkuMapper.getById(100L)).thenReturn(saleableSku());
        when(seckillSkuMapper.insert(any(SeckillSku.class))).thenReturn(1);

        activityService.update(10L, dto);

        verify(seckillSkuMapper).deleteDraftByActivityId(10L);
        verify(seckillSkuMapper).insert(any(SeckillSku.class));
    }

    @Test
    void updateRejectsPublishedActivity() {
        SeckillActivityCreateDto dto = validDto();
        SeckillActivity published = activity(
                10L, SeckillActivityStatus.PUBLISHED.getCode());
        when(activityMapper.updateDraft(
                10L, dto.getName(), dto.getStartTime(), dto.getEndTime()))
                .thenReturn(0);
        when(activityMapper.selectById(10L)).thenReturn(published);

        MyException exception = assertThrows(
                MyException.class, () -> activityService.update(10L, dto));

        assertEquals(ResultCodeEnum.SECKILL_ACTIVITY_STATE_ERROR,
                exception.getResultCodeEnum());
        verify(seckillSkuMapper, never()).deleteDraftByActivityId(10L);
    }

    @Test
    void offlinePublishedActivityClosesRedisAdmissionBeforeEndingDatabaseState() {
        SeckillActivity published = activity(
                10L, SeckillActivityStatus.PUBLISHED.getCode());
        SeckillSku sku = new SeckillSku();
        sku.setSkuId(100L);
        when(activityMapper.selectById(10L)).thenReturn(published);
        when(seckillSkuMapper.findByActivityId(10L))
                .thenReturn(Collections.singletonList(sku));

        activityService.offline(10L);

        InOrder inOrder = inOrder(redisService, lifecycleService);
        inOrder.verify(redisService).deactivate(
                10L, Collections.singletonList(sku));
        inOrder.verify(lifecycleService).beginEnding(10L);
    }

    @Test
    void offlineDraftActivityEndsWithoutCreatingRedisKeys() {
        SeckillActivity draft = activity(
                10L, SeckillActivityStatus.DRAFT.getCode());
        when(activityMapper.selectById(10L)).thenReturn(draft);

        activityService.offline(10L);

        verify(lifecycleService).finishDraft(10L);
        verify(redisService, never()).deactivate(any(), any());
    }

    @Test
    void publishDoesNotReopenActivityThatWasOfflinedConcurrently() {
        SeckillActivity published = activity(
                10L, SeckillActivityStatus.PUBLISHED.getCode());
        SeckillActivity ending = activity(
                10L, SeckillActivityStatus.ENDING.getCode());
        SeckillSku sku = new SeckillSku();
        sku.setSkuId(100L);
        when(activityMapper.selectById(10L)).thenReturn(published, ending);
        when(seckillSkuMapper.findByActivityId(10L))
                .thenReturn(Collections.singletonList(sku));

        MyException exception = assertThrows(
                MyException.class, () -> activityService.publish(10L));

        assertEquals(ResultCodeEnum.SECKILL_ACTIVITY_STATE_ERROR,
                exception.getResultCodeEnum());
        verify(redisService).deactivate(
                10L, Collections.singletonList(sku));
    }

    private SeckillActivityCreateDto validDto() {
        SeckillSkuCreateDto sku = new SeckillSkuCreateDto();
        sku.setSkuId(100L);
        sku.setSeckillPrice(new BigDecimal("99.00"));
        sku.setTotalStock(10);

        SeckillActivityCreateDto dto = new SeckillActivityCreateDto();
        dto.setName("Flash sale");
        dto.setStartTime(new Date(System.currentTimeMillis() + 60_000L));
        dto.setEndTime(new Date(System.currentTimeMillis() + 3_600_000L));
        dto.setSkuList(Collections.singletonList(sku));
        return dto;
    }

    private ProductSku saleableSku() {
        ProductSku sku = new ProductSku();
        sku.setId(100L);
        sku.setStatus(1);
        sku.setIsDeleted(0);
        sku.setSalePrice(new BigDecimal("199.00"));
        sku.setStockNum(100);
        return sku;
    }

    private SeckillActivity activity(Long id, Integer status) {
        SeckillActivity activity = new SeckillActivity();
        activity.setId(id);
        activity.setStatus(status);
        return activity;
    }
}
