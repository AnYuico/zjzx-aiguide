package com.tzp.zjzx.manager.mapper;

import com.tzp.zjzx.model.dto.order.OrderStatisticsDto;
import com.tzp.zjzx.model.entity.order.OrderStatistics;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrderInfoMapper {

    /**
     * 查询订单统计信息
     * @param createDate
     * @return
     */
    OrderStatistics selectStatisticsByDate(String createDate);



}
