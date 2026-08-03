package com.tzp.zjzx.order.mapper;

import com.tzp.zjzx.model.event.order.PaymentSucceededEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PaymentExceptionTaskMapper {

    int insertIgnore(@Param("event") PaymentSucceededEvent event,
                     @Param("reason") String reason);
}
