package com.tzp.zjzx.order.task;

import com.tzp.zjzx.feign.product.ProductFeignClient;
import com.tzp.zjzx.model.entity.order.InventoryOperationTask;
import com.tzp.zjzx.model.enums.InventoryOperationType;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.order.mapper.InventoryOperationTaskMapper;
import com.tzp.zjzx.order.service.SeckillStockReturnService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class InventoryOperationCompensationTask {

    private static final int MAX_RETRIES = 10;

    private final InventoryOperationTaskMapper taskMapper;
    private final ProductFeignClient productFeignClient;
    private final SeckillStockReturnService seckillStockReturnService;

    @Value("${zjzx.internal-api.token}")
    private String internalApiToken;

    public InventoryOperationCompensationTask(InventoryOperationTaskMapper taskMapper,
                                              ProductFeignClient productFeignClient,
                                              SeckillStockReturnService seckillStockReturnService) {
        this.taskMapper = taskMapper;
        this.productFeignClient = productFeignClient;
        this.seckillStockReturnService = seckillStockReturnService;
    }

    @Scheduled(fixedDelayString = "${zjzx.order.inventory-retry-delay-ms:30000}",
            initialDelayString = "${zjzx.order.inventory-retry-initial-delay-ms:60000}")
    public void retryPendingOperations() {
        List<InventoryOperationTask> tasks = taskMapper.findPending(50);
        for (InventoryOperationTask task : tasks) {
            try {
                execute(task);
                taskMapper.markSuccess(task.getOrderNo(), task.getOperationType());
                if (Integer.valueOf(InventoryOperationType.RELEASE.getCode())
                        .equals(task.getOperationType())) {
                    seckillStockReturnService.returnAfterPhysicalRelease(task.getOrderNo());
                }
            } catch (RuntimeException ex) {
                int retryCount = task.getRetryCount() + 1;
                int status = retryCount >= MAX_RETRIES ? 2 : 0;
                long delaySeconds = Math.min(300L, 10L * retryCount);
                taskMapper.markRetry(task.getId(), status, retryCount,
                        new Date(System.currentTimeMillis() + delaySeconds * 1000L),
                        abbreviate(ex.getMessage()));
                log.warn("Inventory operation fallback failed: order={}, operation={}, retry={}",
                        task.getOrderNo(), task.getOperationType(), retryCount, ex);
            }
        }
    }

    private void execute(InventoryOperationTask task) {
        InventoryOperationType operationType =
                InventoryOperationType.fromCode(task.getOperationType());
        Result<Boolean> result = operationType == InventoryOperationType.CONFIRM
                ? productFeignClient.confirmStock(internalApiToken, task.getOrderNo())
                : productFeignClient.releaseStock(internalApiToken, task.getOrderNo());
        if (result == null || !ResultCodeEnum.SUCCESS.getCode().equals(result.getCode())
                || !Boolean.TRUE.equals(result.getData())) {
            throw new IllegalStateException("Product service rejected inventory operation: code="
                    + (result == null ? null : result.getCode()) + ", message="
                    + (result == null ? null : result.getMessage()));
        }
    }

    private String abbreviate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
