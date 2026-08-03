package com.tzp.zjzx.manager.mapper;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SysRoleUserMapper {

    /**
     * 按照用户id删除所有角色数据
     *
     * @param userId
     */
    void deleteByUserId(Long userId);

    /**
     * 为用户分配角色
     *
     * @param userId
     * @param roleId
     */
    void doAssign(Long userId, Long roleId);

    /**
     * 根据userId查询该user被分配过的roleIds
     * @param userId
     * @return
     */
    List<Long> selectRoleIdsByUserId(Long userId);

    List<Long> selectUserIdsByRoleId(Long roleId);
}
