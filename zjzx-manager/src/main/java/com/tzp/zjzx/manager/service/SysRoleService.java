package com.tzp.zjzx.manager.service;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.model.dto.system.SysRoleDto;
import com.tzp.zjzx.model.entity.system.SysRole;

import java.util.Map;

public interface SysRoleService {
    /**
     * 分页查询
     * @param sysRoleDto
     * @param current
     * @param limit
     * @return
     */
    PageInfo<SysRole> findByPage(SysRoleDto sysRoleDto, Integer current, Integer limit);

    /**
     * 角色添加
     * @param sysRole
     */
    void saveSysRole(SysRole sysRole);

    /**
     * 角色修改
     * @param sysRole
     */
    void updateSysRole(SysRole sysRole);


    /**
     * 角色删除
     * @param roleId
     */
    void deleteById(Long roleId);

    /**
     * 查询所有角色
     * @return
     */
    Map<String, Object> findAll(Long userId);

}
