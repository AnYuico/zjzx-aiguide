package com.tzp.zjzx.order.service;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.dto.internal.ProductSkuInternalDto;
import com.tzp.zjzx.model.dto.internal.UserAddressInternalDto;
import com.tzp.zjzx.model.dto.internal.UserProfileInternalDto;
import com.tzp.zjzx.model.entity.order.OrderInfo;
import com.tzp.zjzx.model.entity.order.OrderItem;
import com.tzp.zjzx.model.entity.order.OrderLog;
import com.tzp.zjzx.model.entity.seckill.SeckillSku;
import com.tzp.zjzx.model.enums.OrderSource;
import com.tzp.zjzx.model.event.order.OrderTimeoutEvent;
import com.tzp.zjzx.model.event.seckill.SeckillOrderRequestedEvent;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.mq.MqEventIds;
import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.mq.outbox.MqOutboxService;
import com.tzp.zjzx.order.mapper.OrderInfoMapper;
import com.tzp.zjzx.order.mapper.OrderItemMapper;
import com.tzp.zjzx.order.mapper.OrderLogMapper;
import com.tzp.zjzx.order.mapper.SeckillOrderRequestMapper;
import com.tzp.zjzx.order.mapper.SeckillOrderStockMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Date;

@Service
public class SeckillOrderCreationService {

    private final SeckillOrderStockMapper stockMapper;
    private final SeckillOrderRequestMapper requestMapper;
    private final OrderInfoMapper orderInfoMapper;
    private final OrderItemMapper orderItemMapper;
    private final OrderLogMapper orderLogMapper;
    private final MqOutboxService outboxService;

    @Value("${zjzx.order.payment-timeout-minutes:30}")
    private long paymentTimeoutMinutes;

    public SeckillOrderCreationService(SeckillOrderStockMapper stockMapper,
                                       SeckillOrderRequestMapper requestMapper,
                                       OrderInfoMapper orderInfoMapper,
                                       OrderItemMapper orderItemMapper,
                                       OrderLogMapper orderLogMapper,
                                       MqOutboxService outboxService) {
        this.stockMapper = stockMapper;
        this.requestMapper = requestMapper;
        this.orderInfoMapper = orderInfoMapper;
        this.orderItemMapper = orderItemMapper;
        this.orderLogMapper = orderLogMapper;
        this.outboxService = outboxService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Long create(SeckillOrderRequestedEvent event,
                       UserProfileInternalDto user,
                       UserAddressInternalDto address,
                       ProductSkuInternalDto productSku) {
        SeckillSku seckillSku = stockMapper.selectSku(
                event.getActivityId(), event.getSeckillSkuId(), event.getSkuId());
        if (seckillSku == null
                || seckillSku.getSeckillPrice() == null
                || stockMapper.decrement(event.getActivityId(),
                event.getSeckillSkuId(), event.getSkuId()) != 1) {
            throw new MyException(ResultCodeEnum.SECKILL_SOLD_OUT);
        }

        OrderInfo order = buildOrder(event, user, address, productSku, seckillSku);
        orderInfoMapper.save(order);

        OrderItem item = new OrderItem();
        item.setOrderId(order.getId());
        item.setSkuId(event.getSkuId());
        item.setSkuName(productSku.getSkuName());
        item.setThumbImg(productSku.getThumbImg());
        item.setSkuPrice(seckillSku.getSeckillPrice());
        item.setSkuNum(1);
        orderItemMapper.save(item);

        OrderLog log = new OrderLog();
        log.setOrderId(order.getId());
        log.setOperateUser("SECKILL:" + event.getUserId());
        log.setProcessStatus(0);
        log.setNote("秒杀请求异步创建订单并预占库存");
        orderLogMapper.save(log);

        if (requestMapper.markSuccess(event.getRequestId(), order.getId()) != 1) {
            throw new MyException(ResultCodeEnum.SECKILL_REQUEST_FAILED);
        }

        OrderTimeoutEvent timeoutEvent = new OrderTimeoutEvent(
                MqEventIds.orderTimeout(order.getOrderNo()),
                order.getOrderNo(), order.getExpireTime());
        outboxService.enqueue(timeoutEvent.getEventId(),
                RabbitMqConstants.ORDER_TIMEOUT_EVENT,
                RabbitMqConstants.ORDER_EVENT_EXCHANGE,
                RabbitMqConstants.ORDER_TIMEOUT_DELAY,
                timeoutEvent, order.getExpireTime());
        return order.getId();
    }

    private OrderInfo buildOrder(SeckillOrderRequestedEvent event,
                                 UserProfileInternalDto user,
                                 UserAddressInternalDto address,
                                 ProductSkuInternalDto productSku,
                                 SeckillSku seckillSku) {
        OrderInfo order = new OrderInfo();
        order.setUserId(event.getUserId());
        order.setNickName(user.getNickName());
        order.setOrderNo(event.getOrderNo());
        order.setRequestId(event.getRequestId());
        order.setOrderSource(OrderSource.SECKILL.getCode());
        order.setTotalAmount(seckillSku.getSeckillPrice());
        BigDecimal originalPrice = productSku.getSalePrice() == null
                ? seckillSku.getSeckillPrice() : productSku.getSalePrice();
        order.setOriginalTotalAmount(originalPrice);
        order.setCouponAmount(originalPrice.subtract(seckillSku.getSeckillPrice())
                .max(BigDecimal.ZERO));
        order.setFeightFee(BigDecimal.ZERO);
        order.setPayType(2);
        order.setOrderStatus(0);
        order.setReceiverName(address.getName());
        order.setReceiverPhone(address.getPhone());
        order.setReceiverTagName(address.getTagName());
        order.setReceiverProvince(address.getProvinceCode());
        order.setReceiverCity(address.getCityCode());
        order.setReceiverDistrict(address.getDistrictCode());
        order.setReceiverAddress(address.getFullAddress());
        order.setRemark("秒杀订单");
        order.setExpireTime(new Date(System.currentTimeMillis()
                + paymentTimeoutMinutes * 60_000L));
        return order;
    }
}

