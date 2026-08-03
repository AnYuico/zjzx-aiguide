package com.tzp.zjzx.manager.mapper;

import com.tzp.zjzx.model.entity.base.ProductUnit;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface ProductUnitMapper {
    /**
     * 查询所有
     * @return
     */
    List<ProductUnit> findAll();

}
