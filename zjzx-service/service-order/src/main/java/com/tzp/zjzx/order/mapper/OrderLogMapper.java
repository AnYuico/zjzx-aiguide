package com.tzp.zjzx.order.mapper;

import com.tzp.zjzx.model.entity.order.OrderLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderLogMapper {

    /**
     * 添加数据到order_log表
     * @param orderLog
     */
    void save(OrderLog orderLog);

}
