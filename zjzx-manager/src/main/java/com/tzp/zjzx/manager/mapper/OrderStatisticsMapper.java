package com.tzp.zjzx.manager.mapper;

import com.tzp.zjzx.model.dto.order.OrderStatisticsDto;
import com.tzp.zjzx.model.entity.order.OrderStatistics;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Mapper
public interface OrderStatisticsMapper {
    /**
     * 将统计后的数据添加到结果表
     * @param orderStatistics
     */
    int incrementPaidOrder(@Param("orderDate") Date orderDate,
                           @Param("amount") BigDecimal amount);

    int upsertSnapshot(OrderStatistics orderStatistics);

    /**
     * 查询订单统计信息数据
     * @param orderStatisticsDto
     * @return
     */
    List<OrderStatistics> selectList(OrderStatisticsDto orderStatisticsDto);
}
