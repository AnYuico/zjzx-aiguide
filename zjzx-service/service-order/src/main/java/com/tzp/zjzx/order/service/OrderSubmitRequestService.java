package com.tzp.zjzx.order.service;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.entity.order.OrderSubmitRequest;
import com.tzp.zjzx.model.enums.OrderSubmitRequestStatus;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.order.mapper.OrderSubmitRequestMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Objects;
import java.util.UUID;

@Service
public class OrderSubmitRequestService {

    private final OrderSubmitRequestMapper requestMapper;

    public OrderSubmitRequestService(OrderSubmitRequestMapper requestMapper) {
        this.requestMapper = requestMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public OrderSubmitRequest claim(String requestId, Long userId) {
        if (!StringUtils.hasText(requestId) || requestId.length() > 64 || userId == null) {
            throw new MyException(ResultCodeEnum.ORDER_SUBMIT_REQUEST_INVALID);
        }

        OrderSubmitRequest candidate = new OrderSubmitRequest();
        candidate.setRequestId(requestId);
        candidate.setUserId(userId);
        candidate.setOrderNo(UUID.randomUUID().toString().replace("-", ""));
        candidate.setStatus(OrderSubmitRequestStatus.PROCESSING.getCode());
        requestMapper.insertIgnore(candidate);

        OrderSubmitRequest request = requestMapper.selectByRequestId(requestId);
        if (request == null || !Objects.equals(request.getUserId(), userId)) {
            throw new MyException(ResultCodeEnum.ORDER_SUBMIT_REQUEST_INVALID);
        }
        if (Objects.equals(request.getStatus(), OrderSubmitRequestStatus.FAILED.getCode())) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        return request;
    }

    @Transactional(rollbackFor = Exception.class)
    public void markFailed(String requestId, Long userId) {
        requestMapper.markFailed(requestId, userId);
    }
}
