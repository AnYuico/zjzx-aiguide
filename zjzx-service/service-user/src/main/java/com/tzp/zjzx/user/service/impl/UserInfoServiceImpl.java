package com.tzp.zjzx.user.service.impl;

import com.tzp.zjzx.ai.contract.vo.AgentUserPrincipalVo;
import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.common.security.LoginSessionService;
import com.tzp.zjzx.common.security.PasswordService;
import com.tzp.zjzx.model.dto.h5.UserLoginDto;
import com.tzp.zjzx.model.dto.h5.UserRegisterDto;
import com.tzp.zjzx.model.dto.internal.UserProfileInternalDto;
import com.tzp.zjzx.model.entity.user.UserInfo;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.common.LoginPrincipal;
import com.tzp.zjzx.model.vo.h5.UserInfoVo;
import com.tzp.zjzx.user.mapper.UserInfoMapper;
import com.tzp.zjzx.user.service.UserInfoService;
import com.tzp.zjzx.utils.AuthContextUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;

@Service
public class UserInfoServiceImpl implements UserInfoService {

    private final UserInfoMapper userInfoMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final LoginSessionService loginSessionService;
    private final PasswordService passwordService;

    @Value("${zjzx.user.default-avatar-url}")
    private String defaultAvatarUrl;

    public UserInfoServiceImpl(UserInfoMapper userInfoMapper,
                               RedisTemplate<String, String> redisTemplate,
                               LoginSessionService loginSessionService,
                               PasswordService passwordService) {
        this.userInfoMapper = userInfoMapper;
        this.redisTemplate = redisTemplate;
        this.loginSessionService = loginSessionService;
        this.passwordService = passwordService;
    }

    @Override
    public void register(UserRegisterDto userRegisterDto) {
        String username = userRegisterDto.getUsername();
        String redisCode = redisTemplate.opsForValue().get(username);
        if (!Objects.equals(redisCode, userRegisterDto.getCode())) {
            throw new MyException(ResultCodeEnum.VALIDATECODE_ERROR);
        }

        if (userInfoMapper.selectByUserName(username) != null) {
            throw new MyException(ResultCodeEnum.USER_NAME_IS_EXISTS);
        }

        UserInfo userInfo = new UserInfo();
        userInfo.setUsername(username);
        userInfo.setNickName(userRegisterDto.getNickName());
        userInfo.setPassword(passwordService.encode(userRegisterDto.getPassword()));
        userInfo.setPhone(username);
        userInfo.setStatus(1);
        userInfo.setSex(0);
        userInfo.setAvatar(defaultAvatarUrl);
        userInfoMapper.save(userInfo);
        redisTemplate.delete(username);
    }

    @Override
    public String login(UserLoginDto userLoginDto) {
        UserInfo userInfo = userInfoMapper.selectByUserName(userLoginDto.getUsername());
        if (userInfo == null) {
            throw new MyException(ResultCodeEnum.LOGIN_ERROR);
        }

        if (!passwordService.matches(userLoginDto.getPassword(), userInfo.getPassword())) {
            throw new MyException(ResultCodeEnum.LOGIN_ERROR);
        }
        if (!Integer.valueOf(1).equals(userInfo.getStatus())) {
            throw new MyException(ResultCodeEnum.ACCOUNT_STOP);
        }
        upgradeLegacyPassword(userInfo, userLoginDto.getPassword());

        String token = UUID.randomUUID().toString().replace("-", "");
        loginSessionService.createUserSession(token, userInfo.getId(), userInfo.getUsername());
        return token;
    }

    @Override
    public UserInfoVo getCurrentUserInfo(String token) {
        UserInfo loginUser = AuthContextUtil.getUserInfo();
        if (loginUser == null || loginUser.getId() == null) {
            throw new MyException(ResultCodeEnum.LOGIN_AUTH);
        }

        UserInfo userInfo = requireActiveUser(loginUser.getId());
        UserInfoVo userInfoVo = new UserInfoVo();
        BeanUtils.copyProperties(userInfo, userInfoVo);
        return userInfoVo;
    }

    @Override
    public UserProfileInternalDto getUserProfileById(Long userId) {
        UserInfo userInfo = requireActiveUser(userId);
        return new UserProfileInternalDto(userInfo.getId(), userInfo.getNickName());
    }

    @Override
    public AgentUserPrincipalVo resolveAgentPrincipal(String token) {
        LoginPrincipal principal = loginSessionService.getUserPrincipal(token);
        if (principal == null || principal.getUserId() == null) {
            return null;
        }
        UserInfo userInfo = userInfoMapper.selectById(principal.getUserId());
        if (userInfo == null || !Integer.valueOf(1).equals(userInfo.getStatus())) {
            return null;
        }
        AgentUserPrincipalVo result = new AgentUserPrincipalVo();
        result.setUserId(userInfo.getId());
        result.setNickName(userInfo.getNickName());
        return result;
    }

    private UserInfo requireActiveUser(Long userId) {
        UserInfo userInfo = userInfoMapper.selectById(userId);
        if (userInfo == null || !Integer.valueOf(1).equals(userInfo.getStatus())) {
            throw new MyException(ResultCodeEnum.LOGIN_AUTH);
        }
        return userInfo;
    }

    private void upgradeLegacyPassword(UserInfo userInfo, String rawPassword) {
        if (!passwordService.needsUpgrade(userInfo.getPassword())) {
            return;
        }
        userInfoMapper.upgradePassword(
                userInfo.getId(),
                userInfo.getPassword(),
                passwordService.encode(rawPassword)
        );
    }
}
