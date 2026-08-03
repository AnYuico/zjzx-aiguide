package com.tzp.zjzx.order.task;

import com.tzp.zjzx.model.entity.seckill.SeckillOrderRequest;
import com.tzp.zjzx.model.event.seckill.SeckillOrderRequestedEvent;
import com.tzp.zjzx.order.service.SeckillAdmissionRollbackService;
import com.tzp.zjzx.order.service.SeckillOrderConsumerService;
import com.tzp.zjzx.order.service.SeckillOrderRequestService;
import com.tzp.zjzx.order.service.SeckillStockReturnService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

@Slf4j
@Component
public class SeckillOrderCompensationTask {

    private final SeckillOrderRequestService requestService;
    private final SeckillOrderConsumerService consumerService;
    private final SeckillAdmissionRollbackService rollbackService;
    private final SeckillStockReturnService stockReturnService;

    @Value("${zjzx.seckill.consumer.processing-timeout-ms:120000}")
    private long processingTimeoutMillis;

    public SeckillOrderCompensationTask(
            SeckillOrderRequestService requestService,
            SeckillOrderConsumerService consumerService,
            SeckillAdmissionRollbackService rollbackService,
            SeckillStockReturnService stockReturnService) {
        this.requestService = requestService;
        this.consumerService = consumerService;
        this.rollbackService = rollbackService;
        this.stockReturnService = stockReturnService;
    }

    @Scheduled(fixedDelayString = "${zjzx.seckill.consumer.scan-delay-ms:10000}",
            initialDelayString = "${zjzx.seckill.consumer.scan-initial-delay-ms:30000}")
    public void reconcile() {
        requestService.resetStaleProcessing(new Date(
                System.currentTimeMillis() - Math.max(10_000L, processingTimeoutMillis)));
        retryQueuedRequests();
        rollbackFailedAdmissions();
        restoreReleasedOrders();
    }

    private void retryQueuedRequests() {
        for (SeckillOrderRequest request : requestService.findRetryable(50)) {
            try {
                SeckillOrderRequestedEvent event = rollbackService.fromRequest(request);
                consumerService.process(event);
            } catch (RuntimeException ex) {
                log.warn("Seckill request retry failed: requestId={}",
                        request.getRequestId(), ex);
            }
        }
    }

    private void rollbackFailedAdmissions() {
        for (SeckillOrderRequest request
                : requestService.findFailedWithoutRollback(50)) {
            try {
                rollbackService.rollback(
                        rollbackService.fromRequest(request), request.getFailReason());
            } catch (RuntimeException ex) {
                log.warn("Seckill admission rollback failed: requestId={}",
                        request.getRequestId(), ex);
            }
        }
    }

    private void restoreReleasedOrders() {
        for (SeckillOrderRequest request
                : requestService.findReleasedOrdersWithoutReturn(50)) {
            try {
                stockReturnService.returnAfterPhysicalRelease(request.getOrderNo());
            } catch (RuntimeException ex) {
                log.warn("Seckill released order stock return failed: orderNo={}",
                        request.getOrderNo(), ex);
            }
        }
    }
}
