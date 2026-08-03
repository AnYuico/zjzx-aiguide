package com.tzp.zjzx.user.service.impl;

import com.tzp.zjzx.model.entity.base.Region;
import com.tzp.zjzx.model.vo.h5.RegionVo;
import com.tzp.zjzx.user.mapper.RegionMapper;
import com.tzp.zjzx.user.service.RegionService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RegionServiceImpl implements RegionService {

    private final RegionMapper regionMapper;

    public RegionServiceImpl(RegionMapper regionMapper) {
        this.regionMapper = regionMapper;
    }

    @Override
    public List<RegionVo> findChildren(String parentCode) {
        if (!isValidParentCode(parentCode)) {
            return List.of();
        }
        return regionMapper.findChildren(parentCode).stream()
                .map(this::toRegionVo)
                .collect(Collectors.toList());
    }

    private boolean isValidParentCode(String parentCode) {
        return "0".equals(parentCode) || parentCode != null && parentCode.matches("\\d{6}");
    }

    private RegionVo toRegionVo(Region region) {
        RegionVo regionVo = new RegionVo();
        regionVo.setCode(region.getCode());
        regionVo.setParentCode(String.valueOf(region.getParentCode()));
        regionVo.setName(region.getName());
        regionVo.setLevel(region.getLevel());
        regionVo.setHasChildren(region.getLevel() != null && region.getLevel() < 3);
        return regionVo;
    }
}
