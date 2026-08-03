package com.tzp.zjzx.pay.controller;

import com.alipay.api.AlipayApiException;
import com.alipay.api.internal.util.AlipaySignature;
import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.entity.user.UserInfo;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.pay.properties.AlipayProperties;
import com.tzp.zjzx.pay.service.AlipayService;
import com.tzp.zjzx.pay.service.PaymentInfoService;
import com.tzp.zjzx.utils.AuthContextUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Objects;

@Slf4j
@Controller
@RequestMapping("/api/order/alipay")
public class AlipayController {

    @Autowired
    private AlipayService alipayService;

    @Autowired
    private AlipayProperties alipayProperties;

    @Autowired
    private PaymentInfoService paymentInfoService;

    @Operation(summary="支付宝下单")
    @GetMapping("submitAlipay/{orderNo}")
    @ResponseBody
    public Result<String> submitAlipay(@Parameter(name = "orderNo", description = "订单号", required = true)
                                           @PathVariable(value = "orderNo") String orderNo) {
        UserInfo currentUser = AuthContextUtil.getUserInfo();
        if (currentUser == null || currentUser.getId() == null) {
            throw new MyException(ResultCodeEnum.LOGIN_AUTH);
        }
        String form = alipayService.submitAlipay(orderNo, currentUser.getId());
        return Result.build(form, ResultCodeEnum.SUCCESS);
    }

    @Operation(summary="支付宝异步回调")
    @RequestMapping("callback/notify")
    @ResponseBody
    public String alipayNotify(@RequestParam Map<String, String> paramMap) {
        log.info("AlipayController...alipayNotify方法执行了...");
        boolean signVerified = false; //调用SDK验证签名
        try {
            signVerified = AlipaySignature.rsaCheckV1(paramMap, alipayProperties.getAlipayPublicKey(), AlipayProperties.charset, AlipayProperties.sign_type);
        } catch (AlipayApiException e) {
            log.error("Failed to verify Alipay callback signature", e);
        }

        // 交易状态
        String trade_status = paramMap.get("trade_status");

        // true
        if (signVerified && Objects.equals(alipayProperties.getAppId(), paramMap.get("app_id"))) {
            if ("TRADE_SUCCESS".equals(trade_status) || "TRADE_FINISHED".equals(trade_status)) {
                // 正常的支付成功，我们应该更新交易记录状态
                paymentInfoService.updatePaymentStatus(paramMap);
                return "success";
            }

        }

        return "failure";
    }



}
