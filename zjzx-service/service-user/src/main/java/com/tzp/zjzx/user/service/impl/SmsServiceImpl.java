package com.tzp.zjzx.user.service.impl;

import com.tzp.zjzx.user.service.SmsService;
import com.tzp.zjzx.utils.HttpUtils;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.HttpResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class SmsServiceImpl implements SmsService {


    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Value("${SMS_API_HOST:https://gyytz.market.alicloudapi.com}")
    private String smsApiHost;

    @Value("${SMS_API_PATH:/sms/smsSend}")
    private String smsApiPath;

    @Value("${SMS_APP_CODE:}")
    private String smsAppCode;

    @Value("${SMS_SIGN_ID:}")
    private String smsSignId;

    @Value("${SMS_TEMPLATE_ID:}")
    private String smsTemplateId;

    /**
     * 发送短信验证码
     *
     * @param phone
     */
    @Override
    public void sendCode(String phone) {

        //为了方便测试  固定为6666
        String code = redisTemplate.opsForValue().get(phone);
        if(StringUtils.hasText(code)){
            return;
        }

        //1 生成一个验证码
        code = RandomStringUtils.randomNumeric(4);
        //String code = "54188";
        //2 把生成的验证码放入redis
        redisTemplate.opsForValue().set(phone,code,5, TimeUnit.MINUTES);
        //3 向手机号发送短信验证码
        sendMessage(phone,code);
    }

    /**
     * 向手机号发送短信验证码
     * @param phone
     * @param code
     */
    private void sendMessage(String phone, String code){
        if (!StringUtils.hasText(smsAppCode)
                || !StringUtils.hasText(smsSignId)
                || !StringUtils.hasText(smsTemplateId)) {
            throw new IllegalStateException("SMS service credentials are not configured");
        }
        String method = "POST";
        Map<String, String> headers = new HashMap<String, String>();
        headers.put("Authorization", "APPCODE " + smsAppCode);
        Map<String, String> querys = new HashMap<String, String>();
        querys.put("mobile", phone);
        querys.put("param", "**code**:"+code+",**minute**:5");

//smsSignId（短信前缀）和templateId（短信模板），可登录国阳云控制台自助申请。参考文档：http://help.guoyangyun.com/Problem/Qm.html

        querys.put("smsSignId", smsSignId);
        querys.put("templateId", smsTemplateId);
        Map<String, String> bodys = new HashMap<String, String>();


        try {
            /**
             * 重要提示如下:
             * HttpUtils请从\r\n\t    \t* https://github.com/aliyun/api-gateway-demo-sign-java/blob/master/src/main/java/com/aliyun/api/gateway/demo/util/HttpUtils.java\r\n\t    \t* 下载
             *
             * 相应的依赖请参照
             * https://github.com/aliyun/api-gateway-demo-sign-java/blob/master/pom.xml
             */
            HttpResponse response = HttpUtils.doPost(
                    smsApiHost, smsApiPath, method, headers, querys, bodys);
            System.out.println(response.toString());
            //获取response的body
            //System.out.println(EntityUtils.toString(response.getEntity()));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }


}
