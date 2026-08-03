package com.tzp.zjzx.order.mapper;

import com.tzp.zjzx.model.entity.order.OrderInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface OrderInfoMapper {

    /**
     * 添加数据到order_info表
     */
    void save(OrderInfo orderInfo);

    /**
     * 获取订单信息
     * @param orderId
     * @return
     */
    OrderInfo getByIdAndUserId(@Param("orderId") Long orderId,
                               @Param("userId") Long userId);

    /**
     * 分页查询订单
     * @param userId
     * @param orderStatus
     * @return
     */
    List<OrderInfo> findUserPage(@Param("userId") Long userId,
                                 @Param("orderStatus") Integer orderStatus);

    /**
     * 根据订单号查询订单
     * @param orderNo
     * @return
     */
    OrderInfo getByOrderNo(String orderNo) ;

    OrderInfo getByOrderNoAndUserId(@Param("orderNo") String orderNo,
                                    @Param("userId") Long userId);

    OrderInfo getByOrderNoAndUserIdIncludingDeleted(@Param("orderNo") String orderNo,
                                                     @Param("userId") Long userId);

    OrderInfo getByRequestId(String requestId);

    /**
     * 更新订单状态
     * @param orderInfo
     */
    void updateById(OrderInfo orderInfo);

    int markPaid(@Param("orderNo") String orderNo,
                 @Param("payType") Integer payType,
                 @Param("paymentTime") Date paymentTime);

    int closeExpired(@Param("orderNo") String orderNo,
                     @Param("cancelTime") Date cancelTime,
                     @Param("cancelReason") String cancelReason);

    int cancelPendingByUser(@Param("orderNo") String orderNo,
                            @Param("userId") Long userId,
                            @Param("cancelTime") Date cancelTime,
                            @Param("cancelReason") String cancelReason);

    int hideByUser(@Param("orderNo") String orderNo,
                   @Param("userId") Long userId,
                   @Param("cancelledStatus") Integer cancelledStatus,
                   @Param("completedStatus") Integer completedStatus);

    List<OrderInfo> findExpiredOrders(@Param("limit") int limit);

    List<OrderInfo> findAgentRecentOrders(
            @Param("userId") Long userId,
            @Param("orderStatus") Integer orderStatus,
            @Param("limit") Integer limit);
}
