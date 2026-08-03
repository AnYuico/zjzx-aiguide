package com.tzp.zjzx.manager.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.common.security.LoginSessionService;
import com.tzp.zjzx.common.security.PasswordService;
import com.tzp.zjzx.manager.mapper.SysRoleUserMapper;
import com.tzp.zjzx.manager.mapper.SysUserMapper;
import com.tzp.zjzx.model.dto.system.LoginDto;
import com.tzp.zjzx.model.dto.system.SysUserPasswordDto;
import com.tzp.zjzx.model.dto.system.SysUserUpdateDto;
import com.tzp.zjzx.model.entity.system.SysUser;
import com.tzp.zjzx.model.enums.RedisKeyEnum;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.util.DigestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SysUserServiceImplTest {

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private SysRoleUserMapper sysRoleUserMapper;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private LoginSessionService loginSessionService;

    @Mock
    private PasswordService passwordService;

    private SysUserServiceImpl sysUserService;

    @BeforeEach
    void setUp() {
        sysUserService = new SysUserServiceImpl(
                sysUserMapper,
                sysRoleUserMapper,
                redisTemplate,
                loginSessionService,
                passwordService
        );
    }

    @Test
    void disabledAdminCannotLogin() {
        LoginDto loginDto = new LoginDto();
        loginDto.setUserName("disabled");
        loginDto.setPassword("password");
        loginDto.setCaptcha("ABCD");
        loginDto.setCodeKey("code-key");
        SysUser disabledUser = new SysUser();
        disabledUser.setId(9L);
        disabledUser.setUserName("disabled");
        disabledUser.setStatus(0);
        disabledUser.setPassword(DigestUtils.md5DigestAsHex("password".getBytes()));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyEnum.USER_VALIDATE.getKey("code-key"))).thenReturn("ABCD");
        when(sysUserMapper.selectUserInfoByUserName("disabled")).thenReturn(disabledUser);
        when(passwordService.matches("password", disabledUser.getPassword())).thenReturn(true);

        MyException exception = assertThrows(MyException.class, () -> sysUserService.login(loginDto));

        assertEquals(ResultCodeEnum.ACCOUNT_STOP, exception.getResultCodeEnum());
        verifyNoInteractions(loginSessionService);
    }

    @Test
    void passwordUpdateHashesPasswordAndRevokesSessions() {
        SysUser existingUser = existingUser();
        SysUserPasswordDto update = new SysUserPasswordDto();
        update.setId(7L);
        update.setNewPassword("new-password");
        when(sysUserMapper.selectById(7L)).thenReturn(existingUser);
        when(passwordService.encode("new-password")).thenReturn("bcrypt-hash");

        sysUserService.updatePassword(update);

        verify(sysUserMapper).updatePassword(7L, "bcrypt-hash");
        verify(loginSessionService).revokeAdminSessionsAfterCommit(7L);
    }

    @Test
    void displayOnlyUpdateKeepsCurrentSessions() {
        SysUserUpdateDto update = new SysUserUpdateDto();
        update.setId(7L);
        update.setAvatar("http://example.test/new-avatar.jpg");
        when(sysUserMapper.selectById(7L)).thenReturn(existingUser());

        sysUserService.updateSysUser(update);

        verify(sysUserMapper).update(org.mockito.ArgumentMatchers.argThat(
                user -> user.getId().equals(7L)
                        && "http://example.test/new-avatar.jpg".equals(user.getAvatar())
                        && user.getPassword() == null));
        verify(loginSessionService, never()).revokeAdminSessionsAfterCommit(7L);
    }

    @Test
    void successfulLegacyLoginUpgradesPasswordWithConditionalUpdate() {
        LoginDto loginDto = new LoginDto();
        loginDto.setUserName("admin");
        loginDto.setPassword("password");
        loginDto.setCaptcha("ABCD");
        loginDto.setCodeKey("code-key");
        SysUser user = existingUser();
        String md5 = DigestUtils.md5DigestAsHex("password".getBytes());
        user.setPassword(md5);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(RedisKeyEnum.USER_VALIDATE.getKey("code-key"))).thenReturn("ABCD");
        when(sysUserMapper.selectUserInfoByUserName("admin")).thenReturn(user);
        when(passwordService.matches("password", md5)).thenReturn(true);
        when(passwordService.needsUpgrade(md5)).thenReturn(true);
        when(passwordService.encode("password")).thenReturn("bcrypt-hash");

        sysUserService.login(loginDto);

        verify(sysUserMapper).upgradePassword(7L, md5, "bcrypt-hash");
    }

    private SysUser existingUser() {
        SysUser existingUser = new SysUser();
        existingUser.setId(7L);
        existingUser.setUserName("admin");
        existingUser.setStatus(1);
        return existingUser;
    }
}
