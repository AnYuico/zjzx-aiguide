package com.tzp.zjzx.user.service;

import com.tzp.zjzx.model.vo.h5.RegionVo;

import java.util.List;

public interface RegionService {

    List<RegionVo> findChildren(String parentCode);
}
