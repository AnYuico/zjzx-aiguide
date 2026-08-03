package com.tzp.zjzx.user.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.common.security.LoginSessionService;
import com.tzp.zjzx.common.security.PasswordService;
import com.tzp.zjzx.model.dto.h5.UserLoginDto;
import com.tzp.zjzx.model.entity.user.UserInfo;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.h5.UserInfoVo;
import com.tzp.zjzx.model.vo.common.LoginPrincipal;
import com.tzp.zjzx.ai.contract.vo.AgentUserPrincipalVo;
import com.tzp.zjzx.user.mapper.UserInfoMapper;
import com.tzp.zjzx.utils.AuthContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.util.DigestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserInfoServiceImplTest {

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private LoginSessionService loginSessionService;

    @Mock
    private PasswordService passwordService;

    private UserInfoServiceImpl userInfoService;

    @BeforeEach
    void setUp() {
        userInfoService = new UserInfoServiceImpl(
                userInfoMapper, redisTemplate, loginSessionService, passwordService);
    }

    @AfterEach
    void tearDown() {
        AuthContextUtil.removeUserInfo();
    }

    @Test
    void loginCreatesMinimalUserSession() {
        UserInfo dbUser = new UserInfo();
        dbUser.setId(3L);
        dbUser.setUsername("13800000000");
        dbUser.setPassword(DigestUtils.md5DigestAsHex("password".getBytes()));
        dbUser.setStatus(1);
        UserLoginDto loginDto = new UserLoginDto();
        loginDto.setUsername("13800000000");
        loginDto.setPassword("password");
        when(userInfoMapper.selectByUserName("13800000000")).thenReturn(dbUser);
        when(passwordService.matches("password", dbUser.getPassword())).thenReturn(true);
        when(passwordService.needsUpgrade(dbUser.getPassword())).thenReturn(true);
        when(passwordService.encode("password")).thenReturn("bcrypt-hash");

        String token = userInfoService.login(loginDto);

        verify(loginSessionService).createUserSession(token, 3L, "13800000000");
        verify(userInfoMapper).upgradePassword(3L, dbUser.getPassword(), "bcrypt-hash");
    }

    @Test
    void currentUserInfoReadsLatestProfileFromDatabase() {
        UserInfo loginUser = new UserInfo();
        loginUser.setId(3L);
        AuthContextUtil.setUserInfo(loginUser);
        UserInfo latestUser = new UserInfo();
        latestUser.setId(3L);
        latestUser.setNickName("new-name");
        latestUser.setAvatar("http://example.test/new-avatar.jpg");
        latestUser.setStatus(1);
        when(userInfoMapper.selectById(3L)).thenReturn(latestUser);

        UserInfoVo result = userInfoService.getCurrentUserInfo("unused-token");

        assertEquals("new-name", result.getNickName());
        assertEquals("http://example.test/new-avatar.jpg", result.getAvatar());
    }

    @Test
    void missingUserReturnsLoginErrorInsteadOfNullPointer() {
        UserLoginDto loginDto = new UserLoginDto();
        loginDto.setUsername("missing");
        loginDto.setPassword("password");
        when(userInfoMapper.selectByUserName("missing")).thenReturn(null);

        MyException exception = assertThrows(MyException.class, () -> userInfoService.login(loginDto));

        assertEquals(ResultCodeEnum.LOGIN_ERROR, exception.getResultCodeEnum());
    }

    @Test
    void resolvesAgentPrincipalFromServerSideLoginSession() {
        when(loginSessionService.getUserPrincipal("mall-token"))
                .thenReturn(new LoginPrincipal(3L, "13800000000", 1L));
        UserInfo activeUser = new UserInfo();
        activeUser.setId(3L);
        activeUser.setNickName("test");
        activeUser.setStatus(1);
        when(userInfoMapper.selectById(3L)).thenReturn(activeUser);

        AgentUserPrincipalVo principal =
                userInfoService.resolveAgentPrincipal("mall-token");

        assertEquals(3L, principal.getUserId());
        assertEquals("test", principal.getNickName());
    }

    @Test
    void rejectsDisabledUserDuringAgentPrincipalResolution() {
        when(loginSessionService.getUserPrincipal("mall-token"))
                .thenReturn(new LoginPrincipal(3L, "13800000000", 1L));
        UserInfo disabledUser = new UserInfo();
        disabledUser.setId(3L);
        disabledUser.setStatus(0);
        when(userInfoMapper.selectById(3L)).thenReturn(disabledUser);

        AgentUserPrincipalVo principal =
                userInfoService.resolveAgentPrincipal("mall-token");

        org.junit.jupiter.api.Assertions.assertNull(principal);
    }
}
