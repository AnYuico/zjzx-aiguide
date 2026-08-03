package com.tzp.zjzx.manager.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.CircleCaptcha;
import com.tzp.zjzx.manager.service.ValidateCodeService;
import com.tzp.zjzx.model.enums.RedisKeyEnum;
import com.tzp.zjzx.model.vo.system.ValidateCodeVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
public class ValidateCodeServiceImpl implements ValidateCodeService {

    @Autowired
    private RedisTemplate<String , String> redisTemplate ;

    @Override
    public ValidateCodeVo generateValidateCode() {

        // 使用hutool工具包中的工具类生成图片验证码
        //参数：宽  高  验证码位数 干扰线数量
        CircleCaptcha circleCaptcha = CaptchaUtil.createCircleCaptcha(150, 48, 4, 20);
        String codeValue = circleCaptcha.getCode();
        String imageBase64 = circleCaptcha.getImageBase64();

        // 生成uuid作为图片验证码的key
        String codeKey = UUID.randomUUID().toString().replace("-", "");

        // 将验证码存储到Redis中
        redisTemplate.opsForValue().set(RedisKeyEnum.USER_VALIDATE.getKey(codeKey), codeValue , 5 , TimeUnit.MINUTES);

        // 构建响应结果数据
        ValidateCodeVo validateCodeVo = new ValidateCodeVo() ;
        validateCodeVo.setCodeKey(codeKey);
        validateCodeVo.setCodeValue("data:image/png;base64," + imageBase64);

        // 返回数据
        return validateCodeVo;
    }
}
