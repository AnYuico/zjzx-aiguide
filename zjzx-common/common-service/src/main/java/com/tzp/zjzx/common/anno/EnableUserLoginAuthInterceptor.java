package com.tzp.zjzx.common.anno;

import com.tzp.zjzx.common.config.LoginSessionConfiguration;
import com.tzp.zjzx.common.config.UserWebMvcConfiguration;
import com.tzp.zjzx.common.interceptor.UserLoginAuthInterceptor;
import org.springframework.context.annotation.Import;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(value = RetentionPolicy.RUNTIME)
@Target(value = ElementType.TYPE)
@Import(value = {
        LoginSessionConfiguration.class,
        UserLoginAuthInterceptor.class,
        UserWebMvcConfiguration.class
})
public @interface EnableUserLoginAuthInterceptor {

}
