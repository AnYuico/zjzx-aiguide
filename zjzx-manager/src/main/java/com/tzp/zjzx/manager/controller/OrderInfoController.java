package com.tzp.zjzx.manager.controller;

import com.tzp.zjzx.manager.service.OrderInfoService;
import com.tzp.zjzx.model.dto.order.OrderStatisticsDto;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.order.OrderStatisticsVo;
import org.checkerframework.checker.units.qual.A;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/order/orderInfo")
public class OrderInfoController {

    @Autowired
    private OrderInfoService orderInfoService;

    /**
     * 获取前一天订单统计信息
     * @param orderStatisticsDto
     * @return
     */
    @GetMapping("/getOrderStatisticsData")
    public Result getOrderStatisticsData(OrderStatisticsDto orderStatisticsDto){
       OrderStatisticsVo orderStatisticsVo =
               orderInfoService.getOrderStatisticsData(orderStatisticsDto);
       return Result.build(orderStatisticsVo, ResultCodeEnum.SUCCESS);
    }
}
