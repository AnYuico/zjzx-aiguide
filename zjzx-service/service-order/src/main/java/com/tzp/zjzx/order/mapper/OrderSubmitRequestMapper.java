package com.tzp.zjzx.order.mapper;

import com.tzp.zjzx.model.entity.order.OrderSubmitRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface OrderSubmitRequestMapper {

    int insertIgnore(OrderSubmitRequest request);

    OrderSubmitRequest selectByRequestId(String requestId);

    int markSuccess(@Param("requestId") String requestId,
                    @Param("userId") Long userId,
                    @Param("orderId") Long orderId);

    int markFailed(@Param("requestId") String requestId,
                   @Param("userId") Long userId);
}
