package com.tzp.zjzx.user.mapper;

import com.tzp.zjzx.model.entity.user.UserAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserAddressMapper {
    /**
     * 获取用户地址列表
     * @param userId
     * @return
     */
    List<UserAddress> findUserAddressList(Long userId);

    UserAddress getUserAddress(@Param("id") Long id, @Param("userId") Long userId);

    Long lockUser(@Param("userId") Long userId);

    int countActive(@Param("userId") Long userId);

    int insert(UserAddress userAddress);

    int updateOwned(UserAddress userAddress);

    int clearDefault(@Param("userId") Long userId);

    int logicalDelete(@Param("id") Long id, @Param("userId") Long userId);

    Long findFirstAddressId(@Param("userId") Long userId, @Param("excludeId") Long excludeId);

    int setDefault(@Param("id") Long id, @Param("userId") Long userId);
}
