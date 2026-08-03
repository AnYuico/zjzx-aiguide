package com.tzp.zjzx.user.controller;

import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.user.service.SmsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/user/sms")
public class SmsController {

    @Autowired
    private SmsService smsService;

    /**
     * 根据手机号发送验证码
     * @param phone
     * @return
     */
    @GetMapping("/sendCode/{phone}")
    public Result sendCode(@PathVariable String phone){
         smsService.sendCode(phone);
         return Result.build(null, ResultCodeEnum.SUCCESS);
    }

}
