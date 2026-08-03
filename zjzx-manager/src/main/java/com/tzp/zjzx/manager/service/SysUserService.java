package com.tzp.zjzx.manager.service;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.model.dto.system.AssignRoleDto;
import com.tzp.zjzx.model.dto.system.LoginDto;
import com.tzp.zjzx.model.dto.system.SysUserCreateDto;
import com.tzp.zjzx.model.dto.system.SysUserDto;
import com.tzp.zjzx.model.dto.system.SysUserPasswordDto;
import com.tzp.zjzx.model.dto.system.SysUserUpdateDto;
import com.tzp.zjzx.model.vo.system.LoginVo;
import com.tzp.zjzx.model.vo.system.SysUserInfoVo;
import com.tzp.zjzx.model.vo.system.SysUserListVo;

public interface SysUserService {

    LoginVo login(LoginDto loginDto);

    SysUserInfoVo getCurrentUserInfo(Long userId);

    void logout(String token);

    PageInfo<SysUserListVo> findByPage(SysUserDto sysUserDto, Integer current, Integer limit);

    void saveSysUser(SysUserCreateDto sysUser);

    void updateSysUser(SysUserUpdateDto sysUser);

    void updatePassword(SysUserPasswordDto passwordDto);

    void deleteById(Long userId);

    void doAssign(AssignRoleDto assignRoleDto);
}
