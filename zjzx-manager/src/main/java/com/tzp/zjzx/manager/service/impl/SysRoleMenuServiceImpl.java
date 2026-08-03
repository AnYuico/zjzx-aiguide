package com.tzp.zjzx.manager.service.impl;

import com.tzp.zjzx.manager.mapper.SysRoleMenuMapper;
import com.tzp.zjzx.manager.mapper.SysRoleUserMapper;
import com.tzp.zjzx.common.security.LoginSessionService;
import com.tzp.zjzx.manager.service.SysMenuService;
import com.tzp.zjzx.manager.service.SysRoleMenuService;
import com.tzp.zjzx.model.dto.system.AssignMenuDto;
import com.tzp.zjzx.model.entity.system.SysMenu;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SysRoleMenuServiceImpl implements SysRoleMenuService {

    @Autowired
    private SysRoleMenuMapper sysRoleMenuMapper;

    @Autowired
    private SysMenuService sysMenuService;

    @Autowired
    private SysRoleUserMapper sysRoleUserMapper;

    @Autowired
    private LoginSessionService loginSessionService;

    /**
     * 查询所有菜单 和 该roleId分配过的菜单Id
     * @param roleId
     * @return
     */
    @Override
    public Map<String, Object> findSysRoleMenuByRoleId(Long roleId) {

        //1.查询所有菜单
        List<SysMenu> sysMenuList = sysMenuService.findNodes();

        //2.查询角色分配过的菜单
        List<Long> roleMenuIds = sysRoleMenuMapper.findSysRoleMenuById(roleId);

        HashMap<String , Object> map = new HashMap<>();
        map.put("sysMenuList",sysMenuList);
        map.put("roleMenuIds",roleMenuIds);
        return map;
    }

    /**
     * 按照角色id删除分配过的菜单
     * @param roleId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteByRoleId(Long roleId) {
        List<Long> affectedUserIds = sysRoleUserMapper.selectUserIdsByRoleId(roleId);
        sysRoleMenuMapper.deleteByRoleId(roleId);
        revokeAffectedUsers(affectedUserIds);
    }

    /**
     * 为角色分配菜单
     * @param assignMenuDto
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void doAssign(AssignMenuDto assignMenuDto) {
        List<Long> affectedUserIds = sysRoleUserMapper.selectUserIdsByRoleId(assignMenuDto.getRoleId());
        //删除该角色之前分配过的菜单数据
        sysRoleMenuMapper.deleteByRoleId(assignMenuDto.getRoleId());

        //保存分配的菜单数据
        List<Map<String, Number>> menuInfo = assignMenuDto.getMenuIdList();
        if (menuInfo != null && menuInfo.size() > 0) {
            sysRoleMenuMapper.doAssign(assignMenuDto);
        }
        revokeAffectedUsers(affectedUserIds);
    }

    private void revokeAffectedUsers(List<Long> userIds) {
        if (userIds != null) {
            userIds.forEach(loginSessionService::revokeAdminSessionsAfterCommit);
        }
    }
}
