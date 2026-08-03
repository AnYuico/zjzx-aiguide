package com.tzp.zjzx.manager.service.impl;

import cn.hutool.core.date.DateUtil;
import com.tzp.zjzx.manager.mapper.OrderInfoMapper;
import com.tzp.zjzx.manager.mapper.OrderStatisticsMapper;
import com.tzp.zjzx.manager.service.OrderInfoService;
import com.tzp.zjzx.model.dto.order.OrderStatisticsDto;
import com.tzp.zjzx.model.entity.order.OrderStatistics;
import com.tzp.zjzx.model.vo.order.OrderStatisticsVo;
import org.apache.catalina.manager.host.HostManagerServlet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderInfoServiceImpl implements OrderInfoService {

    @Autowired
    private OrderStatisticsMapper orderStatisticsMapper;

    /**
     * 获取前一天订单统计数据
     * @param orderStatisticsDto
     * @return
     */
    @Override
    public OrderStatisticsVo getOrderStatisticsData(OrderStatisticsDto orderStatisticsDto) {
        //1 根据dto条件查询统计结果数据 返回list集合
        List<OrderStatistics> orderStatisticsList =
                orderStatisticsMapper.selectList(orderStatisticsDto);
        //2 遍历list集合 得到所有日期 并封装到新list集合中
        List<String> dateList = orderStatisticsList.stream()
                .map(orderStatistics -> DateUtil.format(orderStatistics.getOrderDate(), "yyyy-MM-dd"))
                .collect(Collectors.toList());

        //3 遍历新list集合 得到每个日期的总金额 并封装到新list集合中
        List<BigDecimal> decimalList = orderStatisticsList.stream()
                .map(OrderStatistics::getTotalAmount)
                .collect(Collectors.toList());

        //4 将新list集合封装到vo中
        OrderStatisticsVo orderStatisticsVo = new OrderStatisticsVo();
        orderStatisticsVo.setDateList(dateList);
        orderStatisticsVo.setAmountList(decimalList);

        return orderStatisticsVo;
    }
}
