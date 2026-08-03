package com.tzp.zjzx.product.mapper;

import com.tzp.zjzx.model.entity.product.InventoryRequest;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface InventoryRequestMapper {

    int insertIgnore(InventoryRequest inventoryRequest);

    InventoryRequest selectByOrderNoForUpdate(String orderNo);

    int updateStatus(@Param("orderNo") String orderNo,
                     @Param("expectedStatus") Integer expectedStatus,
                     @Param("targetStatus") Integer targetStatus);
}
