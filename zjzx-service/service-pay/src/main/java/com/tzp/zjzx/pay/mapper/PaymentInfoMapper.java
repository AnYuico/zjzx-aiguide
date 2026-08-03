package com.tzp.zjzx.pay.mapper;

import com.tzp.zjzx.model.entity.pay.PaymentInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

@Mapper
public interface PaymentInfoMapper {
    /**
     * 根据订单号查询支付信息
     * @param orderNo
     * @return
     */
    PaymentInfo getByOrderNo(String orderNo);

    /**
     * 保存支付信息
     * @param paymentInfo
     */
    int save(PaymentInfo paymentInfo);

    /**
     * 更新支付信息
     * @param paymentInfo
     */
    void updatePaymentInfo(PaymentInfo paymentInfo);

    int markPaid(@Param("orderNo") String orderNo,
                 @Param("outTradeNo") String outTradeNo,
                 @Param("callbackTime") Date callbackTime,
                 @Param("callbackContent") String callbackContent);
}
