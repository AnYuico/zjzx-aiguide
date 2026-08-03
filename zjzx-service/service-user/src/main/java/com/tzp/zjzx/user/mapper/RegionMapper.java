package com.tzp.zjzx.user.mapper;

import com.tzp.zjzx.model.entity.base.Region;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RegionMapper {

    List<Region> findChildren(@Param("parentCode") String parentCode);

    Region findByCode(@Param("code") String code);
}
