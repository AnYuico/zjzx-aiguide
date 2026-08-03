package com.tzp.zjzx.manager.mapper;

import com.tzp.zjzx.model.dto.system.SysRoleDto;
import com.tzp.zjzx.model.entity.system.SysRole;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysRoleMapper {
    /**
     * 分页查询
     * @param sysRoleDto
     * @return
     */
    List<SysRole> findByPage(SysRoleDto sysRoleDto);

    /**
     * 添加角色
     * @param sysRole
     */
    void save(SysRole sysRole);

    /**
     * 修改角色
     * @param sysRole
     */
    void update(SysRole sysRole);

    /**
     *按照id删除角色
     * @param roleId
     */
    void delete(Long roleId);

    /**
     * 查询所有角色
     * @return
     */
    List<SysRole> findAll();

}
