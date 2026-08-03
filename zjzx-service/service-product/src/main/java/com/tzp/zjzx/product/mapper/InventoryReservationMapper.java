package com.tzp.zjzx.product.mapper;

import com.tzp.zjzx.model.entity.product.InventoryReservation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface InventoryReservationMapper {

    int insert(InventoryReservation reservation);

    int countByOrderNo(String orderNo);

    List<InventoryReservation> selectByOrderNoForUpdate(String orderNo);

    int updateStatus(@Param("orderNo") String orderNo,
                     @Param("skuId") Long skuId,
                     @Param("expectedStatus") Integer expectedStatus,
                     @Param("targetStatus") Integer targetStatus);
}
