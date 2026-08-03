package com.tzp.zjzx.order.mapper;

import com.tzp.zjzx.model.entity.seckill.SeckillOrderRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface SeckillOrderRequestMapper {

    int insertIgnore(SeckillOrderRequest request);

    SeckillOrderRequest selectByRequestId(String requestId);

    SeckillOrderRequest selectByOrderNo(String orderNo);

    SeckillOrderRequest selectByUserSku(@Param("activityId") Long activityId,
                                        @Param("userId") Long userId,
                                        @Param("skuId") Long skuId);

    int markProcessing(String requestId);

    int markSuccess(@Param("requestId") String requestId,
                    @Param("orderId") Long orderId);

    int markRetry(@Param("requestId") String requestId,
                  @Param("retryCount") Integer retryCount,
                  @Param("nextRetryTime") Date nextRetryTime,
                  @Param("failReason") String failReason);

    int markFailed(@Param("requestId") String requestId,
                   @Param("failReason") String failReason);

    int markStockReturned(@Param("requestId") String requestId,
                          @Param("expectedStatus") Integer expectedStatus);

    int markCancelled(String orderNo);

    int resetStaleProcessing(Date staleBefore);

    List<SeckillOrderRequest> findRetryable(int limit);

    List<SeckillOrderRequest> findFailedWithoutRollback(int limit);

    List<SeckillOrderRequest> findReleasedOrdersWithoutReturn(int limit);
}
