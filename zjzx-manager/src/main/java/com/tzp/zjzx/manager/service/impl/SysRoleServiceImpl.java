package com.tzp.zjzx.manager.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.common.security.LoginSessionService;
import com.tzp.zjzx.manager.mapper.SysRoleMapper;
import com.tzp.zjzx.manager.mapper.SysRoleUserMapper;
import com.tzp.zjzx.manager.service.SysRoleService;
import com.tzp.zjzx.model.dto.system.SysRoleDto;
import com.tzp.zjzx.model.entity.system.SysRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SysRoleServiceImpl implements SysRoleService {


    @Autowired
    private SysRoleMapper sysRoleMapper;

    @Autowired
    private SysRoleUserMapper sysRoleUserMapper;

    @Autowired
    private LoginSessionService loginSessionService;

    /**
     * 分页查询
     *
     * @param sysRoleDto
     * @param current
     * @param limit
     * @return
     */
    @Override
    public PageInfo<SysRole> findByPage(SysRoleDto sysRoleDto, Integer current, Integer limit) {
        //1 设置分页参数
        PageHelper.startPage(current, limit);
        //2 根据条件查询所有数据
        List<SysRole> list = sysRoleMapper.findByPage(sysRoleDto);
        //3 封装pageInfo对象
        PageInfo<SysRole> pageInfo = new PageInfo<>(list);

        return pageInfo;
    }

    /**
     * 添加角色
     *
     * @param sysRole
     */
    @Override
    public void saveSysRole(SysRole sysRole) {
        sysRoleMapper.save(sysRole);
    }

    /**
     * 修改角色
     *
     * @param sysRole
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateSysRole(SysRole sysRole) {
        List<Long> affectedUserIds = sysRoleUserMapper.selectUserIdsByRoleId(sysRole.getId());
        sysRoleMapper.update(sysRole);
        revokeAffectedUsers(affectedUserIds);
    }

    /**
     * 按照ID删除角色
     *
     * @param roleId
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteById(Long roleId) {
        List<Long> affectedUserIds = sysRoleUserMapper.selectUserIdsByRoleId(roleId);
        sysRoleMapper.delete(roleId);
        revokeAffectedUsers(affectedUserIds);
    }

    /**
     * 查询所有角色
     *
     * @return
     */
    @Override
    public Map<String, Object> findAll(Long userId) {

        //1.查询所有角色
        List<SysRole> roleList = sysRoleMapper.findAll();

        //2.分配过的角色列表
        //根据用户id，查询用户被分配过的角色id
        List<Long> roleIds = sysRoleUserMapper.selectRoleIdsByUserId(userId);

        Map<String, Object> map = new HashMap<>();
        map.put("allRolesList", roleList);
        map.put("sysUserRoles",roleIds);

        return map;
    }

    private void revokeAffectedUsers(List<Long> userIds) {
        if (userIds != null) {
            userIds.forEach(loginSessionService::revokeAdminSessionsAfterCommit);
        }
    }
}
