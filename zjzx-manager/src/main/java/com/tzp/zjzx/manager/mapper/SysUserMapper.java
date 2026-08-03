package com.tzp.zjzx.manager.mapper;

import com.tzp.zjzx.model.dto.system.SysUserDto;
import com.tzp.zjzx.model.entity.system.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysUserMapper {

    /**
     * 凭用户名查询用户信息
     * @param userName
     * @return
     */
    SysUser selectUserInfoByUserName(String userName);

    SysUser selectById(Long userId);

    /**
     * 用户分页查询
     * @param sysUserDto
     * @return
     */
    List<SysUser> findByPage(SysUserDto sysUserDto);

    /**
     * 添加用户
     * @param sysUser
     */
    void save(SysUser sysUser);

    /**
     * 用户修改
     * @param sysUser
     */
    void update(SysUser sysUser);

    void updatePassword(@Param("userId") Long userId, @Param("password") String password);

    int upgradePassword(@Param("userId") Long userId,
                        @Param("oldPassword") String oldPassword,
                        @Param("newPassword") String newPassword);

    /**
     * 用户删除
     * @param userId
     */
    void delete(Long userId);

}
