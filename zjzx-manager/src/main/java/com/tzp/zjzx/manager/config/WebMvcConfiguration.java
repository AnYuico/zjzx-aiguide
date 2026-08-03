package com.tzp.zjzx.manager.config;

import com.tzp.zjzx.manager.interceptor.LoginAuthInterceptor;
import com.tzp.zjzx.manager.properties.UserProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Component
public class WebMvcConfiguration implements WebMvcConfigurer {

    @Autowired
    private LoginAuthInterceptor loginAuthInterceptor;

    @Autowired
    private UserProperties userProperties;

    /**
     * 拦截器注册
     * @param registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(loginAuthInterceptor)
//                .excludePathPatterns("/admin/system/index/login",   //放行的路径
//                        "/admin/system/index/generateValidateCode")
                .excludePathPatterns(userProperties.getNoAuthUrls())
                .addPathPatterns("/**");                            //拦截的路径
    }

    /**
     * 跨域配置
     * @param registry
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")          // 允许跨域访问的路径
                .allowCredentials(true)                // 是否允许在跨域的情况下传递cookie
                .allowedOriginPatterns("*")            // 允许请求来源的域规则
                .allowedMethods("*")
                .allowedHeaders("*");
    }

}
