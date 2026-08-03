package com.tzp.zjzx.pay.service;

import com.tzp.zjzx.model.entity.pay.PaymentInfo;

import java.util.Map;

public interface PaymentInfoService {

    /**
     * 保存支付信息
     * @param orderNo
     * @param userId 当前登录用户 ID
     * @return
     */
    PaymentInfo savePaymentInfo(String orderNo, Long userId);

    /**
     * 更新支付状态
     * @param paramMap
     */
    void updatePaymentStatus(Map<String, String> paramMap);
}
