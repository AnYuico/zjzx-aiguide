package com.tzp.zjzx.pay.controller;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.entity.user.UserInfo;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.pay.properties.AlipayProperties;
import com.tzp.zjzx.pay.service.AlipayService;
import com.tzp.zjzx.pay.service.PaymentInfoService;
import com.tzp.zjzx.utils.AuthContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlipayControllerAuthTest {

    @Mock
    private AlipayService alipayService;

    @Mock
    private AlipayProperties alipayProperties;

    @Mock
    private PaymentInfoService paymentInfoService;

    @InjectMocks
    private AlipayController alipayController;

    @AfterEach
    void clearUserContext() {
        AuthContextUtil.removeUserInfo();
    }

    @Test
    void submitsPaymentForCurrentUserOnly() {
        UserInfo currentUser = new UserInfo();
        currentUser.setId(7L);
        AuthContextUtil.setUserInfo(currentUser);
        when(alipayService.submitAlipay("order-1", 7L))
                .thenReturn("<form></form>");

        Result<String> result = alipayController.submitAlipay("order-1");

        assertEquals(ResultCodeEnum.SUCCESS.getCode(), result.getCode());
        assertEquals("<form></form>", result.getData());
        verify(alipayService).submitAlipay("order-1", 7L);
    }

    @Test
    void rejectsSubmissionWithoutAuthenticatedUserContext() {
        MyException exception = assertThrows(
                MyException.class,
                () -> alipayController.submitAlipay("order-1")
        );

        assertEquals(ResultCodeEnum.LOGIN_AUTH, exception.getResultCodeEnum());
    }
}
