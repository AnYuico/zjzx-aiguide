package com.tzp.zjzx.order.mapper;

import com.tzp.zjzx.model.entity.seckill.SeckillSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SeckillOrderStockMapper {

    SeckillSku selectSku(@Param("activityId") Long activityId,
                         @Param("seckillSkuId") Long seckillSkuId,
                         @Param("skuId") Long skuId);

    int decrement(@Param("activityId") Long activityId,
                  @Param("seckillSkuId") Long seckillSkuId,
                  @Param("skuId") Long skuId);

    int restore(Long seckillSkuId);
}

