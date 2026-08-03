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
import com.tzp.zjzx.user.service.UserAddressService;
import com.tzp.zjzx.utils.AuthContextUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class UserAddressServiceImpl implements UserAddressService {

    private final UserAddressMapper userAddressMapper;
    private final RegionMapper regionMapper;

    public UserAddressServiceImpl(UserAddressMapper userAddressMapper, RegionMapper regionMapper) {
        this.userAddressMapper = userAddressMapper;
        this.regionMapper = regionMapper;
    }

    /**
     * 查询用户地址列表
     * @return
     */
    @Override
    public List<UserAddressVo> findUserAddressList() {
        Long userId = currentUserId();
        return userAddressMapper.findUserAddressList(userId).stream()
                .map(this::toAddressVo)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAddressVo save(UserAddressRequestDto requestDto) {
        Long userId = currentUserId();
        lockCurrentUser(userId);

        UserAddress userAddress = buildAddress(requestDto, userId);
        boolean firstAddress = userAddressMapper.countActive(userId) == 0;
        boolean makeDefault = firstAddress || Integer.valueOf(1).equals(requestDto.getIsDefault());
        userAddress.setIsDefault(makeDefault ? 1 : 0);
        if (makeDefault) {
            userAddressMapper.clearDefault(userId);
        }

        if (userAddressMapper.insert(userAddress) != 1) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        return toAddressVo(userAddress);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserAddressVo update(Long id, UserAddressRequestDto requestDto) {
        Long userId = currentUserId();
        lockCurrentUser(userId);

        UserAddress existing = findOwnedAddress(id, userId);
        UserAddress userAddress = buildAddress(requestDto, userId);
        userAddress.setId(id);

        int requestedDefault = requestDto.getIsDefault() == null
                ? defaultFlag(existing.getIsDefault())
                : requestDto.getIsDefault();
        if (requestedDefault == 1) {
            userAddressMapper.clearDefault(userId);
        } else if (defaultFlag(existing.getIsDefault()) == 1) {
            Long replacementId = userAddressMapper.findFirstAddressId(userId, id);
            if (replacementId == null) {
                requestedDefault = 1;
            } else {
                requireDefaultUpdate(userAddressMapper.setDefault(replacementId, userId));
            }
        }
        userAddress.setIsDefault(requestedDefault);

        if (userAddressMapper.updateOwned(userAddress) != 1) {
            throw new MyException(ResultCodeEnum.USER_ADDRESS_NOT_FOUND);
        }
        return toAddressVo(userAddress);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long userId = currentUserId();
        lockCurrentUser(userId);

        UserAddress existing = findOwnedAddress(id, userId);
        if (userAddressMapper.logicalDelete(id, userId) != 1) {
            throw new MyException(ResultCodeEnum.USER_ADDRESS_NOT_FOUND);
        }
        if (defaultFlag(existing.getIsDefault()) == 1) {
            Long replacementId = userAddressMapper.findFirstAddressId(userId, null);
            if (replacementId != null) {
                requireDefaultUpdate(userAddressMapper.setDefault(replacementId, userId));
            }
        }
    }

    @Override
    public UserAddressInternalDto getUserAddress(Long id, Long userId) {
        UserAddress userAddress = userAddressMapper.getUserAddress(id, userId);
        if (userAddress == null) {
            return null;
        }
        UserAddressInternalDto internalDto = new UserAddressInternalDto();
        BeanUtils.copyProperties(userAddress, internalDto);
        return internalDto;
    }

    private Long currentUserId() {
        UserInfo userInfo = AuthContextUtil.getUserInfo();
        if (userInfo == null || userInfo.getId() == null) {
            throw new MyException(ResultCodeEnum.LOGIN_AUTH);
        }
        return userInfo.getId();
    }

    private void lockCurrentUser(Long userId) {
        if (userAddressMapper.lockUser(userId) == null) {
            throw new MyException(ResultCodeEnum.LOGIN_AUTH);
        }
    }

    private UserAddress findOwnedAddress(Long id, Long userId) {
        if (id == null || id <= 0) {
            throw new MyException(ResultCodeEnum.USER_ADDRESS_NOT_FOUND);
        }
        UserAddress userAddress = userAddressMapper.getUserAddress(id, userId);
        if (userAddress == null) {
            throw new MyException(ResultCodeEnum.USER_ADDRESS_NOT_FOUND);
        }
        return userAddress;
    }

    private UserAddress buildAddress(UserAddressRequestDto requestDto, Long userId) {
        String provinceCode = requestDto.getProvinceCode().trim();
        String cityCode = requestDto.getCityCode().trim();
        String districtCode = requestDto.getDistrictCode().trim();
        Region province = regionMapper.findByCode(provinceCode);
        Region city = regionMapper.findByCode(cityCode);
        Region district = regionMapper.findByCode(districtCode);
        validateRegionPath(province, city, district, provinceCode, cityCode);

        UserAddress userAddress = new UserAddress();
        userAddress.setUserId(userId);
        userAddress.setName(requestDto.getName().trim());
        userAddress.setPhone(requestDto.getPhone().trim());
        userAddress.setTagName(trimToNull(requestDto.getTagName()));
        userAddress.setProvinceCode(provinceCode);
        userAddress.setCityCode(cityCode);
        userAddress.setDistrictCode(districtCode);
        userAddress.setAddress(requestDto.getAddress().trim());
        userAddress.setFullAddress(
                province.getName() + city.getName() + district.getName() + userAddress.getAddress()
        );
        return userAddress;
    }

    private void validateRegionPath(Region province,
                                    Region city,
                                    Region district,
                                    String provinceCode,
                                    String cityCode) {
        boolean valid = province != null
                && city != null
                && district != null
                && Objects.equals(province.getLevel(), 1)
                && Objects.equals(city.getLevel(), 2)
                && Objects.equals(district.getLevel(), 3)
                && Objects.equals(String.valueOf(city.getParentCode()), provinceCode)
                && Objects.equals(String.valueOf(district.getParentCode()), cityCode);
        if (!valid) {
            throw new MyException(ResultCodeEnum.USER_ADDRESS_REGION_INVALID);
        }
    }

    private int defaultFlag(Integer isDefault) {
        return Integer.valueOf(1).equals(isDefault) ? 1 : 0;
    }

    private void requireDefaultUpdate(int affectedRows) {
        if (affectedRows != 1) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private UserAddressVo toAddressVo(UserAddress userAddress) {
        UserAddressVo addressVo = new UserAddressVo();
        BeanUtils.copyProperties(userAddress, addressVo);
        return addressVo;
    }
}
