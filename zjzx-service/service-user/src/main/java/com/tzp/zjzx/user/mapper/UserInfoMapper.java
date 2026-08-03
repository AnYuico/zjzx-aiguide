package com.tzp.zjzx.user.mapper;

import com.tzp.zjzx.model.entity.user.UserInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserInfoMapper {
    /**
     * 根据用户名查询用户是否存在
     * @param username
     * @return
     */
    UserInfo selectByUserName(String username);

    UserInfo selectById(Long userId);

    /**
     * 添加会员
     * @param userInfo
     */
    void save(UserInfo userInfo);

    int upgradePassword(@Param("userId") Long userId,
                        @Param("oldPassword") String oldPassword,
                        @Param("newPassword") String newPassword);

    int resetTestUser(@Param("userId") Long userId,
                      @Param("marker") String marker,
                      @Param("password") String password,
                      @Param("nickName") String nickName,
                      @Param("phone") String phone,
                      @Param("avatar") String avatar);

}
