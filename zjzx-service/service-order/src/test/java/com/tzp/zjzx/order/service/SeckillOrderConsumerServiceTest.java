package com.tzp.zjzx.order.service;

import com.tzp.zjzx.feign.product.ProductFeignClient;
import com.tzp.zjzx.feign.user.UserFeignClient;
import com.tzp.zjzx.model.entity.seckill.SeckillOrderRequest;
import com.tzp.zjzx.model.enums.SeckillRequestStatus;
import com.tzp.zjzx.model.event.seckill.SeckillOrderRequestedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillOrderConsumerServiceTest {

    @Mock
    private SeckillOrderRequestService requestService;
    @Mock
    private SeckillOrderCreationService creationService;
    @Mock
    private SeckillOrderResultService resultService;
    @Mock
    private SeckillAdmissionRollbackService rollbackService;
    @Mock
    private ProductFeignClient productFeignClient;
    @Mock
    private UserFeignClient userFeignClient;
    @Mock
    private StockReleaseCompensationService releaseCompensationService;

    @Test
    void duplicateSuccessfulEventDoesNotCallRemoteServicesOrCreateAgain() {
        SeckillOrderRequestedEvent event = event();
        SeckillOrderRequest request = request();
        request.setStatus(SeckillRequestStatus.SUCCESS.getCode());
        request.setOrderId(501L);
        when(requestService.prepare(event)).thenReturn(request);
        when(requestService.isSameRequest(request, event)).thenReturn(true);
        SeckillOrderConsumerService service = new SeckillOrderConsumerService(
                requestService, creationService, resultService, rollbackService,
                productFeignClient, userFeignClient, releaseCompensationService);

        service.process(event);

        verify(resultService).success(event, 501L);
        verify(requestService, never()).claim(any());
        verify(userFeignClient, never()).getUserInfo(any(), any());
        verify(productFeignClient, never()).reserveStock(any(), any());
        verify(creationService, never()).create(any(), any(), any(), any());
    }

    private SeckillOrderRequestedEvent event() {
        return new SeckillOrderRequestedEvent(
                "seckill.order:request-1", "request-1", 1L, 11L,
                101L, 33L, 9L, "order-1",
                new BigDecimal("99.00"), new Date());
    }

    private SeckillOrderRequest request() {
        SeckillOrderRequest request = new SeckillOrderRequest();
        request.setRequestId("request-1");
        request.setActivityId(1L);
        request.setSeckillSkuId(11L);
        request.setSkuId(101L);
        request.setUserId(33L);
        request.setUserAddressId(9L);
        request.setOrderNo("order-1");
        return request;
    }
}
