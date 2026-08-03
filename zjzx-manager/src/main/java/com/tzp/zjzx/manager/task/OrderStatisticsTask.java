package com.tzp.zjzx.manager.task;

import cn.hutool.core.date.DateUtil;
import com.tzp.zjzx.manager.mapper.OrderInfoMapper;
import com.tzp.zjzx.manager.mapper.OrderStatisticsMapper;
import com.tzp.zjzx.model.entity.order.OrderStatistics;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

@Component
public class OrderStatisticsTask {

    //添加注解 编写运行规则
    //测试定时任务 每隔5s 执行一次
    /*@Scheduled(cron = "0/5 * * * * ?")
    public void testHello(){
        System.out.println(new Date().toInstant());
    }*/

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderStatisticsMapper orderStatisticsMapper;


    @Scheduled(cron = "0 0 2 * * ?")
    public void orderTotalAmountStatistics() {
        //1 获取前一天的日期
        String createDate =
                DateUtil.offsetDay(new Date(), -1).toString("yyyy-MM-dd");

        //2 根据前一天日期进行统计 前一天的交易金额
        OrderStatistics orderStatistics =
                orderInfoMapper.selectStatisticsByDate(createDate);

        //3 把统计之后的数据添加进统计结果表中
        if (orderStatistics != null){
            orderStatisticsMapper.upsertSnapshot(orderStatistics);
        }
    }
}
