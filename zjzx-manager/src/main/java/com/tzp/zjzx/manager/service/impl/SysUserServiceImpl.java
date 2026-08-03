package com.tzp.zjzx.manager.service.impl;

import cn.hutool.core.util.StrUtil;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.common.security.LoginSessionService;
import com.tzp.zjzx.common.security.PasswordService;
import com.tzp.zjzx.manager.mapper.SysRoleUserMapper;
import com.tzp.zjzx.manager.mapper.SysUserMapper;
import com.tzp.zjzx.manager.service.SysUserService;
import com.tzp.zjzx.model.dto.system.AssignRoleDto;
import com.tzp.zjzx.model.dto.system.LoginDto;
import com.tzp.zjzx.model.dto.system.SysUserCreateDto;
import com.tzp.zjzx.model.dto.system.SysUserDto;
import com.tzp.zjzx.model.dto.system.SysUserPasswordDto;
import com.tzp.zjzx.model.dto.system.SysUserUpdateDto;
import com.tzp.zjzx.model.entity.system.SysUser;
import com.tzp.zjzx.model.enums.RedisKeyEnum;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.system.LoginVo;
import com.tzp.zjzx.model.vo.system.SysUserInfoVo;
import com.tzp.zjzx.model.vo.system.SysUserListVo;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SysUserServiceImpl implements SysUserService {

    private final SysUserMapper sysUserMapper;
    private final SysRoleUserMapper sysRoleUserMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final LoginSessionService loginSessionService;
    private final PasswordService passwordService;

    public SysUserServiceImpl(SysUserMapper sysUserMapper,
                              SysRoleUserMapper sysRoleUserMapper,
                              RedisTemplate<String, String> redisTemplate,
                              LoginSessionService loginSessionService,
                              PasswordService passwordService) {
        this.sysUserMapper = sysUserMapper;
        this.sysRoleUserMapper = sysRoleUserMapper;
        this.redisTemplate = redisTemplate;
        this.loginSessionService = loginSessionService;
        this.passwordService = passwordService;
    }

    @Override
    public LoginVo login(LoginDto loginDto) {
        String codeKey = loginDto.getCodeKey();
        String redisCode = redisTemplate.opsForValue().get(RedisKeyEnum.USER_VALIDATE.getKey(codeKey));
        if (StrUtil.isEmpty(redisCode) || !StrUtil.equalsIgnoreCase(redisCode, loginDto.getCaptcha())) {
            throw new MyException(ResultCodeEnum.VALIDATECODE_ERROR);
        }
        redisTemplate.delete(RedisKeyEnum.USER_VALIDATE.getKey(codeKey));

        SysUser sysUser = sysUserMapper.selectUserInfoByUserName(loginDto.getUserName());
        if (sysUser == null || !passwordService.matches(loginDto.getPassword(), sysUser.getPassword())) {
            throw new MyException(ResultCodeEnum.LOGIN_ERROR);
        }
        if (!Integer.valueOf(1).equals(sysUser.getStatus())) {
            throw new MyException(ResultCodeEnum.ACCOUNT_STOP);
        }
        upgradeLegacyPassword(sysUser, loginDto.getPassword());

        String token = UUID.randomUUID().toString().replace("-", "");
        loginSessionService.createAdminSession(token, sysUser.getId(), sysUser.getUserName());
        LoginVo loginVo = new LoginVo();
        loginVo.setToken(token);
        return loginVo;
    }

    @Override
    public SysUserInfoVo getCurrentUserInfo(Long userId) {
        SysUser sysUser = requireActiveUser(userId);
        SysUserInfoVo userInfoVo = new SysUserInfoVo();
        BeanUtils.copyProperties(sysUser, userInfoVo);
        return userInfoVo;
    }

    @Override
    public void logout(String token) {
        loginSessionService.logoutAdminSession(token);
    }

    @Override
    public PageInfo<SysUserListVo> findByPage(SysUserDto sysUserDto, Integer current, Integer limit) {
        PageHelper.startPage(current, limit);
        List<SysUser> users = sysUserMapper.findByPage(sysUserDto);
        PageInfo<SysUser> entityPage = new PageInfo<>(users);
        PageInfo<SysUserListVo> resultPage = new PageInfo<>();
        BeanUtils.copyProperties(entityPage, resultPage, "list");
        resultPage.setList(users.stream().map(this::toListVo).collect(Collectors.toList()));
        return resultPage;
    }

    @Override
    public void saveSysUser(SysUserCreateDto createDto) {
        if (sysUserMapper.selectUserInfoByUserName(createDto.getUserName()) != null) {
            throw new MyException(ResultCodeEnum.USER_NAME_IS_EXISTS);
        }
        SysUser sysUser = new SysUser();
        BeanUtils.copyProperties(createDto, sysUser, "password");
        sysUser.setPassword(passwordService.encode(createDto.getPassword()));
        sysUser.setStatus(1);
        sysUserMapper.save(sysUser);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSysUser(SysUserUpdateDto updateDto) {
        SysUser existingUser = requireUser(updateDto.getId());
        boolean statusChanged = updateDto.getStatus() != null
                && !Objects.equals(existingUser.getStatus(), updateDto.getStatus());
        boolean usernameChanged = StringUtils.hasText(updateDto.getUserName())
                && !Objects.equals(existingUser.getUserName(), updateDto.getUserName());

        SysUser update = new SysUser();
        BeanUtils.copyProperties(updateDto, update);
        sysUserMapper.update(update);
        if (statusChanged || usernameChanged) {
            loginSessionService.revokeAdminSessionsAfterCommit(updateDto.getId());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updatePassword(SysUserPasswordDto passwordDto) {
        requireUser(passwordDto.getId());
        sysUserMapper.updatePassword(passwordDto.getId(), passwordService.encode(passwordDto.getNewPassword()));
        loginSessionService.revokeAdminSessionsAfterCommit(passwordDto.getId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long userId) {
        sysUserMapper.delete(userId);
        loginSessionService.revokeAdminSessionsAfterCommit(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void doAssign(AssignRoleDto assignRoleDto) {
        sysRoleUserMapper.deleteByUserId(assignRoleDto.getUserId());
        List<Long> roleIds = assignRoleDto.getRoleIdList();
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                sysRoleUserMapper.doAssign(assignRoleDto.getUserId(), roleId);
            }
        }
        loginSessionService.revokeAdminSessionsAfterCommit(assignRoleDto.getUserId());
    }

    private void upgradeLegacyPassword(SysUser sysUser, String rawPassword) {
        if (!passwordService.needsUpgrade(sysUser.getPassword())) {
            return;
        }
        sysUserMapper.upgradePassword(
                sysUser.getId(),
                sysUser.getPassword(),
                passwordService.encode(rawPassword)
        );
    }

    private SysUser requireUser(Long userId) {
        SysUser sysUser = userId == null ? null : sysUserMapper.selectById(userId);
        if (sysUser == null) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        return sysUser;
    }

    private SysUser requireActiveUser(Long userId) {
        SysUser sysUser = requireUser(userId);
        if (!Integer.valueOf(1).equals(sysUser.getStatus())) {
            throw new MyException(ResultCodeEnum.LOGIN_AUTH);
        }
        return sysUser;
    }

    private SysUserListVo toListVo(SysUser sysUser) {
        SysUserListVo listVo = new SysUserListVo();
        BeanUtils.copyProperties(sysUser, listVo);
        return listVo;
    }
}
