package com.tzp.zjzx.order.service;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.feign.product.ProductFeignClient;
import com.tzp.zjzx.feign.user.UserFeignClient;
import com.tzp.zjzx.model.dto.internal.ProductSkuInternalDto;
import com.tzp.zjzx.model.dto.internal.UserAddressInternalDto;
import com.tzp.zjzx.model.dto.internal.UserProfileInternalDto;
import com.tzp.zjzx.model.dto.product.StockItemDto;
import com.tzp.zjzx.model.dto.product.StockReserveRequest;
import com.tzp.zjzx.model.entity.seckill.SeckillOrderRequest;
import com.tzp.zjzx.model.enums.SeckillRequestStatus;
import com.tzp.zjzx.model.event.seckill.SeckillOrderRequestedEvent;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class SeckillOrderConsumerService {

    private final SeckillOrderRequestService requestService;
    private final SeckillOrderCreationService creationService;
    private final SeckillOrderResultService resultService;
    private final SeckillAdmissionRollbackService rollbackService;
    private final ProductFeignClient productFeignClient;
    private final UserFeignClient userFeignClient;
    private final StockReleaseCompensationService releaseCompensationService;

    @Value("${zjzx.internal-api.token}")
    private String internalApiToken;

    @Value("${zjzx.seckill.consumer.max-retries:10}")
    private int maxRetries;

    public SeckillOrderConsumerService(SeckillOrderRequestService requestService,
                                       SeckillOrderCreationService creationService,
                                       SeckillOrderResultService resultService,
                                       SeckillAdmissionRollbackService rollbackService,
                                       ProductFeignClient productFeignClient,
                                       UserFeignClient userFeignClient,
                                       StockReleaseCompensationService releaseCompensationService) {
        this.requestService = requestService;
        this.creationService = creationService;
        this.resultService = resultService;
        this.rollbackService = rollbackService;
        this.productFeignClient = productFeignClient;
        this.userFeignClient = userFeignClient;
        this.releaseCompensationService = releaseCompensationService;
    }

    public void process(SeckillOrderRequestedEvent event) {
        validate(event);
        SeckillOrderRequest request = requestService.prepare(event);
        if (!requestService.isSameRequest(request, event)) {
            rollbackAdmission(event, "USER_LIMIT_CONFLICT");
            return;
        }
        if (Objects.equals(request.getStatus(), SeckillRequestStatus.SUCCESS.getCode())) {
            writeSuccess(event, request.getOrderId());
            return;
        }
        if (Objects.equals(request.getStatus(), SeckillRequestStatus.FAILED.getCode())
                || Objects.equals(request.getStatus(), SeckillRequestStatus.CANCELLED.getCode())) {
            if (Objects.equals(request.getStatus(), SeckillRequestStatus.FAILED.getCode())) {
                rollbackAdmission(event, request.getFailReason());
            } else {
                writeCancelled(event, request.getOrderId());
            }
            return;
        }
        if (!requestService.claim(event.getRequestId())) {
            return;
        }

        writeProcessing(event);
        boolean stockReserved = false;
        try {
            ProductSkuInternalDto productSku = requireProduct(event);
            reservePhysicalStock(event);
            stockReserved = true;
            UserProfileInternalDto user = requireUser(event);
            UserAddressInternalDto address = requireAddress(event);

            Long orderId = creationService.create(event, user, address, productSku);
            writeSuccess(event, orderId);
        } catch (RuntimeException ex) {
            SeckillOrderRequest current = requestService.find(event.getRequestId());
            if (current != null
                    && Objects.equals(current.getStatus(), SeckillRequestStatus.SUCCESS.getCode())) {
                writeSuccess(event, current.getOrderId());
                return;
            }
            handleFailure(event, request, ex, stockReserved);
        }
    }

    private void handleFailure(SeckillOrderRequestedEvent event,
                               SeckillOrderRequest request,
                               RuntimeException cause,
                               boolean stockReserved) {
        int nextRetry = request.getRetryCount() == null
                ? 1 : request.getRetryCount() + 1;
        if (!isPermanent(cause) && nextRetry < Math.max(1, maxRetries)) {
            requestService.retry(request, cause);
            log.warn("Seckill order creation will retry: requestId={}, retry={}",
                    event.getRequestId(), nextRetry, cause);
            return;
        }

        requestService.fail(event.getRequestId(), failureMessage(cause));
        if (stockReserved) {
            releasePhysicalStock(event.getOrderNo(), cause);
        }
        rollbackAdmission(event, failureMessage(cause));
        log.error("Seckill order creation failed permanently: requestId={}",
                event.getRequestId(), cause);
    }

    private UserProfileInternalDto requireUser(SeckillOrderRequestedEvent event) {
        UserProfileInternalDto user =
                userFeignClient.getUserInfo(internalApiToken, event.getUserId());
        if (user == null || user.getUserId() == null
                || !Objects.equals(user.getUserId(), event.getUserId())) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        return user;
    }

    private UserAddressInternalDto requireAddress(SeckillOrderRequestedEvent event) {
        UserAddressInternalDto address = userFeignClient.getUserAddress(
                internalApiToken, event.getUserId(), event.getUserAddressId());
        if (address == null || address.getId() == null
                || !Objects.equals(address.getId(), event.getUserAddressId())) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        return address;
    }

    private ProductSkuInternalDto requireProduct(SeckillOrderRequestedEvent event) {
        Result<ProductSkuInternalDto> result =
                productFeignClient.getBySkuId(internalApiToken, event.getSkuId());
        if (!isSuccessful(result) || result.getData() == null
                || !Objects.equals(result.getData().getId(), event.getSkuId())
                || !Integer.valueOf(1).equals(result.getData().getStatus())
                || !Integer.valueOf(0).equals(result.getData().getIsDeleted())) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        return result.getData();
    }

    private void reservePhysicalStock(SeckillOrderRequestedEvent event) {
        StockReserveRequest request = new StockReserveRequest();
        request.setOrderNo(event.getOrderNo());
        request.setItems(List.of(new StockItemDto(event.getSkuId(), 1)));
        Result<Boolean> result =
                productFeignClient.reserveStock(internalApiToken, request);
        if (isSuccessful(result) && Boolean.TRUE.equals(result.getData())) {
            return;
        }
        if (result != null
                && ResultCodeEnum.STOCK_LESS.getCode().equals(result.getCode())) {
            throw new MyException(ResultCodeEnum.STOCK_LESS);
        }
        if (result != null
                && ResultCodeEnum.STOCK_REQUEST_INVALID.getCode().equals(result.getCode())) {
            throw new MyException(ResultCodeEnum.STOCK_REQUEST_INVALID);
        }
        if (result != null
                && ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR.getCode()
                .equals(result.getCode())) {
            throw new MyException(ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR);
        }
        throw new MyException(ResultCodeEnum.SYSTEM_ERROR);
    }

    private void releasePhysicalStock(String orderNo, RuntimeException cause) {
        try {
            Result<Boolean> result =
                    productFeignClient.releaseStock(internalApiToken, orderNo);
            if (!isSuccessful(result) || !Boolean.TRUE.equals(result.getData())) {
                throw new IllegalStateException("Physical stock release was rejected");
            }
        } catch (RuntimeException releaseException) {
            cause.addSuppressed(releaseException);
            releaseCompensationService.record(orderNo);
        }
    }

    private boolean isPermanent(RuntimeException cause) {
        if (!(cause instanceof MyException)) {
            return false;
        }
        MyException myException = (MyException) cause;
        Integer code = myException.getCode();
        return ResultCodeEnum.DATA_ERROR.getCode().equals(code)
                || ResultCodeEnum.STOCK_LESS.getCode().equals(code)
                || ResultCodeEnum.STOCK_REQUEST_INVALID.getCode().equals(code)
                || ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR.getCode().equals(code)
                || ResultCodeEnum.SECKILL_SOLD_OUT.getCode().equals(code)
                || ResultCodeEnum.SECKILL_REQUEST_FAILED.getCode().equals(code);
    }

    private boolean isSuccessful(Result<?> result) {
        return result != null
                && ResultCodeEnum.SUCCESS.getCode().equals(result.getCode());
    }

    private String failureMessage(Throwable cause) {
        if (cause instanceof MyException
                && StringUtils.hasText(((MyException) cause).getMessage())) {
            return ((MyException) cause).getMessage();
        }
        return StringUtils.hasText(cause.getMessage())
                ? cause.getMessage() : "SECKILL_ORDER_CREATE_FAILED";
    }

    private void writeProcessing(SeckillOrderRequestedEvent event) {
        try {
            resultService.processing(event);
        } catch (RuntimeException ex) {
            log.warn("Failed to update seckill processing result: requestId={}",
                    event.getRequestId(), ex);
        }
    }

    private void writeSuccess(SeckillOrderRequestedEvent event, Long orderId) {
        try {
            resultService.success(event, orderId);
        } catch (RuntimeException ex) {
            log.warn("Failed to update seckill success result: requestId={}",
                    event.getRequestId(), ex);
        }
    }

    private void writeFailed(SeckillOrderRequestedEvent event, String message) {
        try {
            resultService.failed(event, message);
        } catch (RuntimeException ex) {
            log.warn("Failed to update seckill failure result: requestId={}",
                    event.getRequestId(), ex);
        }
    }

    private void rollbackAdmission(SeckillOrderRequestedEvent event, String message) {
        try {
            rollbackService.rollback(event, message);
        } catch (RuntimeException ex) {
            writeFailed(event, message);
            log.warn("Failed to rollback seckill admission: requestId={}",
                    event.getRequestId(), ex);
        }
    }

    private void writeCancelled(SeckillOrderRequestedEvent event, Long orderId) {
        try {
            resultService.cancelled(event, orderId);
        } catch (RuntimeException ex) {
            log.warn("Failed to update seckill cancelled result: requestId={}",
                    event.getRequestId(), ex);
        }
    }

    private void validate(SeckillOrderRequestedEvent event) {
        if (event == null
                || !StringUtils.hasText(event.getEventId())
                || !StringUtils.hasText(event.getRequestId())
                || event.getRequestId().length() > 64
                || event.getActivityId() == null
                || event.getSeckillSkuId() == null
                || event.getSkuId() == null
                || event.getUserId() == null
                || event.getUserAddressId() == null
                || !StringUtils.hasText(event.getOrderNo())
                || event.getOrderNo().length() > 64) {
            throw new IllegalArgumentException("Invalid seckill order event");
        }
    }
}
