package com.tzp.zjzx.manager.mapper;

import com.tzp.zjzx.model.dto.system.AssignMenuDto;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysRoleMenuMapper {

    /**
     * 1.查询角色Id分配过的菜单Id
     * @param roleId
     * @return
     */
    List<Long> findSysRoleMenuById(Long roleId);

    /**
     * 2.根据id删除角色分配的菜单数据
     * @param roleId
     */
    void deleteByRoleId(Long roleId);

    /**
     * 3.为角色分配菜单
     * @param assignMenuDto
     */
    void doAssign(AssignMenuDto assignMenuDto);

    /**
     * 4.根据id修改菜单的IsHalf设置为半开 1
     * @param menuId
     */
    void updateSysRoleMenuIsHalf(Long menuId);
}
