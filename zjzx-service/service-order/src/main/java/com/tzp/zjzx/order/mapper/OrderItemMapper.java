package com.tzp.zjzx.order.mapper;

import com.tzp.zjzx.model.entity.order.OrderItem;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface OrderItemMapper {

    /**
     * 添加数据到order_item表
     * @param orderItem
     */
    void save(OrderItem orderItem);

    /**
     * 根据订单id查询订单项
     * @param id
     * @return
     */
    List<OrderItem> findByOrderId(Long id);
}
