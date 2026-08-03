package com.tzp.zjzx.order.service;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.entity.seckill.SeckillOrderRequest;
import com.tzp.zjzx.model.enums.SeckillRequestStatus;
import com.tzp.zjzx.model.event.seckill.SeckillOrderRequestedEvent;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.order.mapper.SeckillOrderRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.Objects;

@Service
public class SeckillOrderRequestService {

    private final SeckillOrderRequestMapper requestMapper;

    public SeckillOrderRequestService(SeckillOrderRequestMapper requestMapper) {
        this.requestMapper = requestMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public SeckillOrderRequest prepare(SeckillOrderRequestedEvent event) {
        SeckillOrderRequest candidate = fromEvent(event);
        requestMapper.insertIgnore(candidate);

        SeckillOrderRequest request = requestMapper.selectByRequestId(event.getRequestId());
        if (request == null) {
            SeckillOrderRequest existing = requestMapper.selectByUserSku(
                    event.getActivityId(), event.getUserId(), event.getSkuId());
            if (existing != null) {
                return existing;
            }
            throw new MyException(ResultCodeEnum.SECKILL_REQUEST_FAILED);
        }
        validateSameRequest(request, event);
        return request;
    }

    public boolean claim(String requestId) {
        return requestMapper.markProcessing(requestId) == 1;
    }

    public SeckillOrderRequest find(String requestId) {
        return requestMapper.selectByRequestId(requestId);
    }

    public void retry(SeckillOrderRequest request, Throwable cause) {
        int retryCount = request.getRetryCount() == null
                ? 1 : request.getRetryCount() + 1;
        long delaySeconds = Math.min(60L, 5L * retryCount);
        requestMapper.markRetry(request.getRequestId(), retryCount,
                new Date(System.currentTimeMillis() + delaySeconds * 1000L),
                abbreviate(cause.getMessage()));
    }

    public void fail(String requestId, String reason) {
        requestMapper.markFailed(requestId, abbreviate(reason));
    }

    public void resetStaleProcessing(Date staleBefore) {
        requestMapper.resetStaleProcessing(staleBefore);
    }

    public List<SeckillOrderRequest> findRetryable(int limit) {
        return requestMapper.findRetryable(limit);
    }

    public List<SeckillOrderRequest> findFailedWithoutRollback(int limit) {
        return requestMapper.findFailedWithoutRollback(limit);
    }

    public List<SeckillOrderRequest> findReleasedOrdersWithoutReturn(int limit) {
        return requestMapper.findReleasedOrdersWithoutReturn(limit);
    }

    public boolean isSameRequest(SeckillOrderRequest request,
                                 SeckillOrderRequestedEvent event) {
        return request != null
                && Objects.equals(request.getRequestId(), event.getRequestId())
                && Objects.equals(request.getActivityId(), event.getActivityId())
                && Objects.equals(request.getSeckillSkuId(), event.getSeckillSkuId())
                && Objects.equals(request.getSkuId(), event.getSkuId())
                && Objects.equals(request.getUserId(), event.getUserId())
                && Objects.equals(request.getUserAddressId(), event.getUserAddressId())
                && Objects.equals(request.getOrderNo(), event.getOrderNo());
    }

    private SeckillOrderRequest fromEvent(SeckillOrderRequestedEvent event) {
        SeckillOrderRequest request = new SeckillOrderRequest();
        request.setRequestId(event.getRequestId());
        request.setActivityId(event.getActivityId());
        request.setSeckillSkuId(event.getSeckillSkuId());
        request.setSkuId(event.getSkuId());
        request.setUserId(event.getUserId());
        request.setUserAddressId(event.getUserAddressId());
        request.setOrderNo(event.getOrderNo());
        request.setStatus(SeckillRequestStatus.QUEUED.getCode());
        return request;
    }

    private void validateSameRequest(SeckillOrderRequest request,
                                     SeckillOrderRequestedEvent event) {
        if (!isSameRequest(request, event)) {
            throw new MyException(ResultCodeEnum.SECKILL_REQUEST_FAILED);
        }
    }

    private String abbreviate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
