package com.tzp.zjzx.manager.service;

import com.tzp.zjzx.model.dto.order.OrderStatisticsDto;
import com.tzp.zjzx.model.vo.order.OrderStatisticsVo;

public interface OrderInfoService {
    /**
     * 获取订单统计信息
     * @param orderStatisticsDto
     * @return
     */
    OrderStatisticsVo getOrderStatisticsData(OrderStatisticsDto orderStatisticsDto);
}
