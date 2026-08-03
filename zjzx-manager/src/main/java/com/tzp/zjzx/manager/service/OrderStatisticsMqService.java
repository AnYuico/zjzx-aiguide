package com.tzp.zjzx.manager.service;

import com.tzp.zjzx.manager.mapper.OrderStatisticsMapper;
import com.tzp.zjzx.mq.RabbitMqConstants;
import com.tzp.zjzx.mq.consume.MqConsumeLogRepository;
import com.tzp.zjzx.model.event.order.OrderPaidEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class OrderStatisticsMqService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final OrderStatisticsMapper statisticsMapper;
    private final MqConsumeLogRepository consumeLogRepository;

    public OrderStatisticsMqService(OrderStatisticsMapper statisticsMapper,
                                    MqConsumeLogRepository consumeLogRepository) {
        this.statisticsMapper = statisticsMapper;
        this.consumeLogRepository = consumeLogRepository;
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordPaidOrder(OrderPaidEvent event) {
        validate(event);
        if (!consumeLogRepository.tryClaim(RabbitMqConstants.MANAGER_ORDER_PAID_CONSUMER,
                event.getEventId(), RabbitMqConstants.ORDER_PAID_EVENT)) {
            return;
        }
        LocalDate paidDate = Instant.ofEpochMilli(event.getPaidAt().getTime())
                .atZone(BUSINESS_ZONE).toLocalDate();
        statisticsMapper.incrementPaidOrder(Date.valueOf(paidDate), event.getTotalAmount());
    }

    private void validate(OrderPaidEvent event) {
        if (event == null || !StringUtils.hasText(event.getEventId())
                || !StringUtils.hasText(event.getOrderNo())
                || event.getTotalAmount() == null
                || event.getTotalAmount().compareTo(BigDecimal.ZERO) < 0
                || event.getPaidAt() == null) {
            throw new IllegalArgumentException("Invalid order paid event");
        }
    }
}
