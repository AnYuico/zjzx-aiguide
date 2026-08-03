package com.tzp.zjzx.user.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.dto.h5.UserAddressRequestDto;
import com.tzp.zjzx.model.dto.internal.UserAddressInternalDto;
import com.tzp.zjzx.model.entity.base.Region;
import com.tzp.zjzx.model.entity.user.UserAddress;
import com.tzp.zjzx.model.entity.user.UserInfo;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.h5.UserAddressVo;
import com.tzp.zjzx.user.mapper.RegionMapper;
import com.tzp.zjzx.user.mapper.UserAddressMapper;
import com.tzp.zjzx.utils.AuthContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAddressServiceImplTest {

    @Mock
    private UserAddressMapper userAddressMapper;

    @Mock
    private RegionMapper regionMapper;

    @InjectMocks
    private UserAddressServiceImpl userAddressService;

    @AfterEach
    void clearUserContext() {
        AuthContextUtil.removeUserInfo();
    }

    @Test
    void addressListUsesCurrentUserId() {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(11L);
        AuthContextUtil.setUserInfo(userInfo);
        UserAddress address = new UserAddress();
        address.setId(21L);
        List<UserAddress> expected = List.of(address);
        when(userAddressMapper.findUserAddressList(11L)).thenReturn(expected);

        List<UserAddressVo> actual = userAddressService.findUserAddressList();

        assertEquals(21L, actual.get(0).getId());
        assertNotSame(address, actual.get(0));
        verify(userAddressMapper).findUserAddressList(11L);
    }

    @Test
    void internalAddressLookupRequiresAddressAndOwnerIds() {
        UserAddress expected = new UserAddress();
        expected.setId(21L);
        when(userAddressMapper.getUserAddress(21L, 11L)).thenReturn(expected);

        UserAddressInternalDto actual = userAddressService.getUserAddress(21L, 11L);

        assertEquals(21L, actual.getId());
        assertNotSame(expected, actual);
        verify(userAddressMapper).getUserAddress(21L, 11L);
    }

    @Test
    void firstAddressIsSavedAsDefaultWithServerBuiltFullAddress() {
        setCurrentUser(11L);
        UserAddressRequestDto requestDto = addressRequest();
        when(userAddressMapper.lockUser(11L)).thenReturn(11L);
        when(userAddressMapper.countActive(11L)).thenReturn(0);
        mockValidRegionPath();
        when(userAddressMapper.insert(any(UserAddress.class))).thenAnswer(invocation -> {
            UserAddress address = invocation.getArgument(0);
            address.setId(31L);
            return 1;
        });

        UserAddressVo result = userAddressService.save(requestDto);

        assertEquals(31L, result.getId());
        assertEquals(1, result.getIsDefault());
        assertEquals("省名称市名称区名称科技路1号", result.getFullAddress());
        verify(userAddressMapper).clearDefault(11L);
    }

    @Test
    void updateRejectsAddressOwnedByAnotherUser() {
        setCurrentUser(11L);
        when(userAddressMapper.lockUser(11L)).thenReturn(11L);
        when(userAddressMapper.getUserAddress(99L, 11L)).thenReturn(null);

        MyException exception = assertThrows(
                MyException.class,
                () -> userAddressService.update(99L, addressRequest())
        );

        assertEquals(ResultCodeEnum.USER_ADDRESS_NOT_FOUND, exception.getResultCodeEnum());
        verifyNoInteractions(regionMapper);
    }

    @Test
    void deleteDefaultAddressPromotesRemainingAddress() {
        setCurrentUser(11L);
        UserAddress existing = new UserAddress();
        existing.setId(21L);
        existing.setIsDefault(1);
        when(userAddressMapper.lockUser(11L)).thenReturn(11L);
        when(userAddressMapper.getUserAddress(21L, 11L)).thenReturn(existing);
        when(userAddressMapper.logicalDelete(21L, 11L)).thenReturn(1);
        when(userAddressMapper.findFirstAddressId(11L, null)).thenReturn(22L);
        when(userAddressMapper.setDefault(22L, 11L)).thenReturn(1);

        userAddressService.delete(21L);

        verify(userAddressMapper).setDefault(22L, 11L);
    }

    @Test
    void saveRejectsInvalidRegionHierarchy() {
        setCurrentUser(11L);
        when(userAddressMapper.lockUser(11L)).thenReturn(11L);
        when(regionMapper.findByCode("110000")).thenReturn(region("110000", 0L, 1, "省名称"));
        when(regionMapper.findByCode("110100")).thenReturn(region("110100", 120000L, 2, "市名称"));
        when(regionMapper.findByCode("110101")).thenReturn(region("110101", 110100L, 3, "区名称"));

        MyException exception = assertThrows(
                MyException.class,
                () -> userAddressService.save(addressRequest())
        );

        assertEquals(ResultCodeEnum.USER_ADDRESS_REGION_INVALID, exception.getResultCodeEnum());
    }

    @Test
    void unauthenticatedAddressListIsRejected() {
        MyException exception = assertThrows(
                MyException.class,
                () -> userAddressService.findUserAddressList()
        );

        assertEquals(ResultCodeEnum.LOGIN_AUTH, exception.getResultCodeEnum());
    }

    private void setCurrentUser(Long userId) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(userId);
        AuthContextUtil.setUserInfo(userInfo);
    }

    private UserAddressRequestDto addressRequest() {
        UserAddressRequestDto requestDto = new UserAddressRequestDto();
        requestDto.setName("测试用户");
        requestDto.setPhone("13800138000");
        requestDto.setTagName("家");
        requestDto.setProvinceCode("110000");
        requestDto.setCityCode("110100");
        requestDto.setDistrictCode("110101");
        requestDto.setAddress("科技路1号");
        requestDto.setIsDefault(0);
        return requestDto;
    }

    private void mockValidRegionPath() {
        when(regionMapper.findByCode("110000")).thenReturn(region("110000", 0L, 1, "省名称"));
        when(regionMapper.findByCode("110100")).thenReturn(region("110100", 110000L, 2, "市名称"));
        when(regionMapper.findByCode("110101")).thenReturn(region("110101", 110100L, 3, "区名称"));
    }

    private Region region(String code, Long parentCode, Integer level, String name) {
        Region region = new Region();
        region.setCode(code);
        region.setParentCode(parentCode);
        region.setLevel(level);
        region.setName(name);
        return region;
    }
}
