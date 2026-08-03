package com.tzp.zjzx.user.service;

import com.tzp.zjzx.model.dto.h5.UserAddressRequestDto;
import com.tzp.zjzx.model.dto.internal.UserAddressInternalDto;
import com.tzp.zjzx.model.vo.h5.UserAddressVo;

import java.util.List;

public interface UserAddressService {
    /**
     * 获取用户地址列表
     * @return
     */
    List<UserAddressVo> findUserAddressList();

    UserAddressVo save(UserAddressRequestDto requestDto);

    UserAddressVo update(Long id, UserAddressRequestDto requestDto);

    void delete(Long id);

    UserAddressInternalDto getUserAddress(Long id, Long userId);
}
