package com.tzp.zjzx.common.interceptor;

import com.alibaba.fastjson.JSON;
import com.tzp.zjzx.common.security.LoginSessionService;
import com.tzp.zjzx.model.entity.user.UserInfo;
import com.tzp.zjzx.model.vo.common.LoginPrincipal;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.utils.AuthContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.Nullable;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

public class UserLoginAuthInterceptor implements HandlerInterceptor {

    private final LoginSessionService loginSessionService;

    public UserLoginAuthInterceptor(LoginSessionService loginSessionService) {
        this.loginSessionService = loginSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        LoginPrincipal principal = loginSessionService.getUserPrincipal(request.getHeader("token"));
        if (principal == null) {
            responseNoLoginInfo(response);
            return false;
        }

        UserInfo currentUser = new UserInfo();
        currentUser.setId(principal.getUserId());
        currentUser.setUsername(principal.getUsername());
        AuthContextUtil.setUserInfo(currentUser);
        return true;
    }

    private void responseNoLoginInfo(HttpServletResponse response) throws IOException {
        Result<Object> result = Result.build(null, ResultCodeEnum.LOGIN_AUTH);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().print(JSON.toJSONString(result));
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler,
                                @Nullable Exception ex) {
        AuthContextUtil.removeUserInfo();
    }
}
