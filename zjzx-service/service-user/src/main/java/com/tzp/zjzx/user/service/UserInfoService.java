package com.tzp.zjzx.user.service;

import com.tzp.zjzx.ai.contract.vo.AgentUserPrincipalVo;
import com.tzp.zjzx.model.dto.h5.UserLoginDto;
import com.tzp.zjzx.model.dto.h5.UserRegisterDto;
import com.tzp.zjzx.model.dto.internal.UserProfileInternalDto;
import com.tzp.zjzx.model.vo.h5.UserInfoVo;

public interface UserInfoService {
    /**
     * 会员注册
     * @param userRegisterDto
     */
    void register(UserRegisterDto userRegisterDto);

    /**
     *  登录
     * @param userLoginDto
     * @return
     */
    String login(UserLoginDto userLoginDto);

    /**
     * 获取用户信息并返回
     * @param token
     * @return
     */
    UserInfoVo getCurrentUserInfo(String token);

    UserProfileInternalDto getUserProfileById(Long userId);

    AgentUserPrincipalVo resolveAgentPrincipal(String token);
}
