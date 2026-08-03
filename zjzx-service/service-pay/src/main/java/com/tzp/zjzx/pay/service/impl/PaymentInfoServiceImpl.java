package com.tzp.zjzx.pay.service.impl;

import com.alibaba.fastjson.JSON;
import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.feign.order.OrderFeignClient;
import com.tzp.zjzx.mq.MqEventIds;
import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.mq.outbox.MqOutboxService;
import com.tzp.zjzx.model.dto.internal.OrderPaymentInternalDto;
import com.tzp.zjzx.model.entity.pay.PaymentInfo;
import com.tzp.zjzx.model.enums.OrderStatus;
import com.tzp.zjzx.model.event.order.PaymentSucceededEvent;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.pay.mapper.PaymentInfoMapper;
import com.tzp.zjzx.pay.service.PaymentInfoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import java.math.BigDecimal;
import java.util.Date;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PaymentInfoServiceImpl implements PaymentInfoService {

    @Autowired
    private PaymentInfoMapper paymentInfoMapper;

    @Autowired
    private OrderFeignClient orderFeignClient;

    @Autowired
    private MqOutboxService mqOutboxService;

    @Value("${zjzx.internal-api.token}")
    private String internalApiToken;

    /**
     * 保存支付信息
     * @param orderNo
     * @param userId 当前登录用户 ID
     * @return
     */
    @Override
    public PaymentInfo savePaymentInfo(String orderNo, Long userId) {
        if (orderNo == null || orderNo.isBlank() || orderNo.length() > 64
                || userId == null || userId <= 0) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }

        Result<OrderPaymentInternalDto> orderResult =
                orderFeignClient.getOrderInfoByOrderNo(internalApiToken, orderNo);
        if (orderResult == null) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        if (!Integer.valueOf(200).equals(orderResult.getCode())) {
            if (ResultCodeEnum.ORDER_NOT_FOUND.getCode().equals(orderResult.getCode())) {
                throw new MyException(ResultCodeEnum.ORDER_NOT_FOUND);
            }
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        OrderPaymentInternalDto orderInfo = orderResult.getData();
        if (orderInfo == null) {
            throw new MyException(ResultCodeEnum.ORDER_NOT_FOUND);
        }
        validateOrderForPayment(orderNo, userId, orderInfo);

        // 每次发起支付都先校验最新订单状态；已有支付记录不能绕过归属和金额校验。
        PaymentInfo paymentInfo = paymentInfoMapper.getByOrderNo(orderNo);
        if (paymentInfo == null) {
            paymentInfo = new PaymentInfo();
            paymentInfo.setUserId(orderInfo.getUserId());
            paymentInfo.setPayType(orderInfo.getPayType());
            String content = orderInfo.getSkuNames() == null
                    ? ""
                    : orderInfo.getSkuNames().stream().collect(Collectors.joining(" "));
            //遍历订单项
            paymentInfo.setContent(content);
            paymentInfo.setAmount(orderInfo.getTotalAmount());
            paymentInfo.setOrderNo(orderNo);
            paymentInfo.setPaymentStatus(0);
            paymentInfoMapper.save(paymentInfo);
            paymentInfo = paymentInfoMapper.getByOrderNo(orderNo);
        }
        validatePaymentRecord(paymentInfo, orderInfo, userId);
        return paymentInfo;
    }

    private void validateOrderForPayment(
            String orderNo,
            Long userId,
            OrderPaymentInternalDto orderInfo) {
        if (orderInfo.getUserId() == null
                || orderInfo.getOrderNo() == null
                || orderInfo.getTotalAmount() == null
                || orderInfo.getPayType() == null
                || orderInfo.getOrderStatus() == null) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        if (!Objects.equals(orderNo, orderInfo.getOrderNo())) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        if (!Objects.equals(userId, orderInfo.getUserId())) {
            throw new MyException(ResultCodeEnum.ORDER_NOT_FOUND);
        }
        if (!Integer.valueOf(OrderStatus.WAITING_PAYMENT.getCode())
                .equals(orderInfo.getOrderStatus())) {
            throw new MyException(ResultCodeEnum.ORDER_CANNOT_PAY);
        }
        if (orderInfo.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
    }

    private void validatePaymentRecord(
            PaymentInfo paymentInfo,
            OrderPaymentInternalDto orderInfo,
            Long userId) {
        if (paymentInfo == null
                || !Objects.equals(paymentInfo.getOrderNo(), orderInfo.getOrderNo())
                || !Objects.equals(paymentInfo.getUserId(), userId)
                || !Objects.equals(paymentInfo.getPayType(), orderInfo.getPayType())
                || paymentInfo.getAmount() == null
                || paymentInfo.getAmount().compareTo(orderInfo.getTotalAmount()) != 0) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        if (!Integer.valueOf(0).equals(paymentInfo.getPaymentStatus())) {
            throw new MyException(ResultCodeEnum.ORDER_CANNOT_PAY);
        }
    }

    /**
     * 更新支付状态
     * @param map
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void updatePaymentStatus(Map<String, String> map) {
        String orderNo = map.get("out_trade_no");
        PaymentInfo paymentInfo = paymentInfoMapper.getByOrderNo(orderNo);
        if (paymentInfo == null) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }

        String totalAmount = map.get("total_amount");
        if (totalAmount == null || paymentInfo.getAmount() == null
                || paymentInfo.getAmount().compareTo(new BigDecimal(totalAmount)) != 0) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }

        Date callbackTime = new Date();
        int updated = paymentInfoMapper.markPaid(
                orderNo,
                map.get("trade_no"),
                callbackTime,
                JSON.toJSONString(map));
        PaymentInfo paidPayment = paymentInfoMapper.getByOrderNo(orderNo);
        if (paidPayment == null || !Integer.valueOf(1).equals(paidPayment.getPaymentStatus())) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }

        Date paidAt = updated == 1 ? callbackTime : paidPayment.getCallbackTime();
        PaymentSucceededEvent event = new PaymentSucceededEvent(
                MqEventIds.paymentSucceeded(orderNo),
                orderNo,
                paidPayment.getOutTradeNo(),
                paidPayment.getPayType(),
                paidPayment.getAmount(),
                paidAt == null ? callbackTime : paidAt);
        mqOutboxService.enqueue(event.getEventId(),
                RabbitMqConstants.PAYMENT_SUCCEEDED_EVENT,
                RabbitMqConstants.ORDER_EVENT_EXCHANGE,
                RabbitMqConstants.PAYMENT_SUCCEEDED,
                event);
    }
}
