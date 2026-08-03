package com.tzp.zjzx.user.service;

public interface SmsService {
    /**
     * 发送短信验证码
     * @param phone
     */
    void sendCode(String phone);
}
