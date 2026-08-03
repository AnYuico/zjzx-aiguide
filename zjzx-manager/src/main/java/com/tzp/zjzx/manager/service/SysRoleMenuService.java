package com.tzp.zjzx.manager.service;

import com.tzp.zjzx.model.dto.system.AssignMenuDto;

import java.util.Map;

public interface SysRoleMenuService {
    /**
     * 查询所有菜单 和 该roleId分配过的菜单Id
     * @param roleId
     * @return
     */
    Map<String, Object> findSysRoleMenuByRoleId(Long roleId);

    /**
     * 根据角色id删除分配的菜单数据
     * @param roleId
     */
    void deleteByRoleId(Long roleId);

    /**
     * 为角色分配菜单
     * @param assignMenuDto
     */
    void doAssign(AssignMenuDto assignMenuDto);
}
