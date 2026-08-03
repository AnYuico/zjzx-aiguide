package com.tzp.zjzx.manager.service.impl;

import com.tzp.zjzx.common.security.LoginSessionService;
import com.tzp.zjzx.manager.mapper.SysRoleMapper;
import com.tzp.zjzx.manager.mapper.SysRoleMenuMapper;
import com.tzp.zjzx.manager.mapper.SysRoleUserMapper;
import com.tzp.zjzx.manager.service.SysMenuService;
import com.tzp.zjzx.model.dto.system.AssignMenuDto;
import com.tzp.zjzx.model.entity.system.SysRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoleSessionRevocationTest {

    @Mock
    private SysRoleMapper sysRoleMapper;

    @Mock
    private SysRoleMenuMapper sysRoleMenuMapper;

    @Mock
    private SysRoleUserMapper sysRoleUserMapper;

    @Mock
    private SysMenuService sysMenuService;

    @Mock
    private LoginSessionService loginSessionService;

    @InjectMocks
    private SysRoleServiceImpl sysRoleService;

    @InjectMocks
    private SysRoleMenuServiceImpl sysRoleMenuService;

    @Test
    void roleUpdateRevokesSessionsOfAssignedUsers() {
        SysRole role = new SysRole();
        role.setId(5L);
        when(sysRoleUserMapper.selectUserIdsByRoleId(5L)).thenReturn(List.of(7L, 8L));

        sysRoleService.updateSysRole(role);

        verify(sysRoleMapper).update(role);
        verify(loginSessionService).revokeAdminSessionsAfterCommit(7L);
        verify(loginSessionService).revokeAdminSessionsAfterCommit(8L);
    }

    @Test
    void menuAssignmentRevokesSessionsOfRoleUsers() {
        AssignMenuDto assignMenuDto = new AssignMenuDto();
        assignMenuDto.setRoleId(5L);
        when(sysRoleUserMapper.selectUserIdsByRoleId(5L)).thenReturn(List.of(7L));

        sysRoleMenuService.doAssign(assignMenuDto);

        verify(sysRoleMenuMapper).deleteByRoleId(5L);
        verify(loginSessionService).revokeAdminSessionsAfterCommit(7L);
    }
}
