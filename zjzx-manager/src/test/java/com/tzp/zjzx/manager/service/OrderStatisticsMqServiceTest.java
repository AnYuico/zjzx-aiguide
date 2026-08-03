package com.tzp.zjzx.manager.service;

import com.tzp.zjzx.manager.mapper.OrderStatisticsMapper;
import com.tzp.zjzx.mq.consume.MqConsumeLogRepository;
import com.tzp.zjzx.model.event.order.OrderPaidEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderStatisticsMqServiceTest {

    @Mock
    private OrderStatisticsMapper statisticsMapper;
    @Mock
    private MqConsumeLogRepository consumeLogRepository;

    private OrderStatisticsMqService service;

    @BeforeEach
    void setUp() {
        service = new OrderStatisticsMqService(statisticsMapper, consumeLogRepository);
    }

    @Test
    void paidEventIncrementsBusinessDateOnce() {
        OrderPaidEvent event = event();
        when(consumeLogRepository.tryClaim(anyString(), anyString(), anyString())).thenReturn(true);

        service.recordPaidOrder(event);

        verify(statisticsMapper).incrementPaidOrder(
                Date.valueOf("2026-07-22"), new BigDecimal("25.00"));
    }

    @Test
    void duplicatePaidEventDoesNotIncrementAgain() {
        OrderPaidEvent event = event();
        when(consumeLogRepository.tryClaim(anyString(), anyString(), anyString())).thenReturn(false);

        service.recordPaidOrder(event);

        verify(statisticsMapper, never()).incrementPaidOrder(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private OrderPaidEvent event() {
        java.util.Date paidAt = java.util.Date.from(
                LocalDateTime.of(2026, 7, 22, 10, 0)
                        .atZone(ZoneId.of("Asia/Shanghai")).toInstant());
        return new OrderPaidEvent("order.paid:order-1", "order-1",
                new BigDecimal("25.00"), paidAt);
    }
}
