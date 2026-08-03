package com.tzp.zjzx.user.controller;

import com.tzp.zjzx.common.security.InternalApiAuth;
import com.tzp.zjzx.model.dto.h5.UserLoginDto;
import com.tzp.zjzx.model.dto.h5.UserRegisterDto;
import com.tzp.zjzx.model.dto.internal.UserProfileInternalDto;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.h5.UserInfoVo;
import com.tzp.zjzx.user.service.UserInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

@Tag(name = "会员用户接口")
@RestController
@RequestMapping("api/user/userInfo")
public class UserInfoController {

    @Autowired
    private UserInfoService userInfoService;

    @Value("${zjzx.internal-api.token}")
    private String internalApiToken;

    /**
     * 会员注册
     * @param userRegisterDto
     * @return
     */
    @Operation(summary = "会员注册")
    @PostMapping("register")
    public Result register(@RequestBody UserRegisterDto userRegisterDto) {
        userInfoService.register(userRegisterDto);
        return Result.build(null , ResultCodeEnum.SUCCESS) ;
    }

    /**
     * 登录
     * @param userLoginDto
     * @return
     */
    @Operation(summary = "会员登录")
    @PostMapping("login")
    public Result login(@RequestBody UserLoginDto userLoginDto) {
        String token = userInfoService.login(userLoginDto);
        return Result.build(token, ResultCodeEnum.SUCCESS);
    }

    @Operation(summary = "获取当前登录用户信息")
    @GetMapping("auth/getCurrentUserInfo")
    public Result<UserInfoVo> getCurrentUserInfo(HttpServletRequest request) {
        String token = request.getHeader("token");
        UserInfoVo userInfoVo = userInfoService.getCurrentUserInfo(token) ;
        return Result.build(userInfoVo , ResultCodeEnum.SUCCESS) ;
    }

    @GetMapping("internal/getUserInfo/{userId}")
    public UserProfileInternalDto getUserInfo(@RequestHeader(InternalApiAuth.HEADER_NAME) String token,
                                              @PathVariable("userId") Long userId) {
        InternalApiAuth.verify(internalApiToken, token);
        return userInfoService.getUserProfileById(userId);
    }
}
