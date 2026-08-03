package com.tzp.zjzx.order.service;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.dto.internal.ProductSkuInternalDto;
import com.tzp.zjzx.model.dto.internal.UserAddressInternalDto;
import com.tzp.zjzx.model.dto.internal.UserProfileInternalDto;
import com.tzp.zjzx.model.entity.order.OrderInfo;
import com.tzp.zjzx.model.entity.seckill.SeckillSku;
import com.tzp.zjzx.model.event.seckill.SeckillOrderRequestedEvent;
import com.tzp.zjzx.mq.outbox.MqOutboxService;
import com.tzp.zjzx.order.mapper.OrderInfoMapper;
import com.tzp.zjzx.order.mapper.OrderItemMapper;
import com.tzp.zjzx.order.mapper.OrderLogMapper;
import com.tzp.zjzx.order.mapper.SeckillOrderRequestMapper;
import com.tzp.zjzx.order.mapper.SeckillOrderStockMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillOrderCreationServiceTest {

    @Mock
    private SeckillOrderStockMapper stockMapper;
    @Mock
    private SeckillOrderRequestMapper requestMapper;
    @Mock
    private OrderInfoMapper orderInfoMapper;
    @Mock
    private OrderItemMapper orderItemMapper;
    @Mock
    private OrderLogMapper orderLogMapper;
    @Mock
    private MqOutboxService outboxService;

    private SeckillOrderCreationService service;

    @BeforeEach
    void setUp() {
        service = new SeckillOrderCreationService(stockMapper, requestMapper,
                orderInfoMapper, orderItemMapper, orderLogMapper, outboxService);
        ReflectionTestUtils.setField(service, "paymentTimeoutMinutes", 30L);
    }

    @Test
    void conditionalActivityStockFailureDoesNotCreateOrder() {
        SeckillOrderRequestedEvent event = event();
        when(stockMapper.selectSku(1L, 11L, 101L)).thenReturn(seckillSku());
        when(stockMapper.decrement(1L, 11L, 101L)).thenReturn(0);

        assertThrows(MyException.class, () -> service.create(
                event, user(), address(), product()));

        verify(orderInfoMapper, never()).save(any());
        verify(requestMapper, never()).markSuccess(any(), any());
    }

    @Test
    void successfulCreationPersistsRequestAndTimeoutOutbox() {
        SeckillOrderRequestedEvent event = event();
        when(stockMapper.selectSku(1L, 11L, 101L)).thenReturn(seckillSku());
        when(stockMapper.decrement(1L, 11L, 101L)).thenReturn(1);
        when(requestMapper.markSuccess("request-1", 501L)).thenReturn(1);
        doAnswer(invocation -> {
            invocation.<OrderInfo>getArgument(0).setId(501L);
            return null;
        }).when(orderInfoMapper).save(any());

        Long orderId = service.create(event, user(), address(), product());

        org.junit.jupiter.api.Assertions.assertEquals(501L, orderId);
        verify(requestMapper).markSuccess("request-1", 501L);
        verify(outboxService).enqueue(any(), any(), any(), any(), any(), any());
    }

    private SeckillOrderRequestedEvent event() {
        return new SeckillOrderRequestedEvent(
                "seckill.order:request-1", "request-1", 1L, 11L,
                101L, 33L, 9L, "order-1",
                new BigDecimal("99.00"), new Date());
    }

    private SeckillSku seckillSku() {
        SeckillSku sku = new SeckillSku();
        sku.setId(11L);
        sku.setActivityId(1L);
        sku.setSkuId(101L);
        sku.setSeckillPrice(new BigDecimal("99.00"));
        return sku;
    }

    private UserProfileInternalDto user() {
        return new UserProfileInternalDto(33L, "test");
    }

    private UserAddressInternalDto address() {
        UserAddressInternalDto address = new UserAddressInternalDto();
        address.setId(9L);
        address.setName("Tester");
        address.setPhone("13800000000");
        address.setProvinceCode("11");
        address.setCityCode("1101");
        address.setDistrictCode("110101");
        address.setFullAddress("Test address");
        return address;
    }

    private ProductSkuInternalDto product() {
        ProductSkuInternalDto product = new ProductSkuInternalDto();
        product.setId(101L);
        product.setSkuName("Test SKU");
        product.setSalePrice(new BigDecimal("199.00"));
        product.setStatus(1);
        product.setIsDeleted(0);
        return product;
    }
}
