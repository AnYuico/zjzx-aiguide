package com.tzp.zjzx.manager.interceptor;

import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson.JSON;
import com.tzp.zjzx.common.security.LoginSessionService;
import com.tzp.zjzx.model.entity.system.SysUser;
import com.tzp.zjzx.model.vo.common.LoginPrincipal;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.utils.AuthContextUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class LoginAuthInterceptor implements HandlerInterceptor {

    private final LoginSessionService loginSessionService;

    public LoginAuthInterceptor(LoginSessionService loginSessionService) {
        this.loginSessionService = loginSessionService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equals(request.getMethod())) {
            return true;
        }

        String token = request.getHeader("token");
        if (StrUtil.isEmpty(token)) {
            responseNoLoginInfo(response);
            return false;
        }

        LoginPrincipal principal = loginSessionService.getAdminPrincipal(token);
        if (principal == null) {
            responseNoLoginInfo(response);
            return false;
        }

        SysUser currentUser = new SysUser();
        currentUser.setId(principal.getUserId());
        currentUser.setUserName(principal.getUsername());
        AuthContextUtil.set(currentUser);
        loginSessionService.refreshAdminSession(token, principal.getUserId());
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
        AuthContextUtil.remove();
    }
}
