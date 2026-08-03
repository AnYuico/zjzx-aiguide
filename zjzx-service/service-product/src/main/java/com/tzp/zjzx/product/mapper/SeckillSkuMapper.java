package com.tzp.zjzx.product.mapper;

import com.tzp.zjzx.model.entity.seckill.SeckillSku;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeckillSkuMapper {

    int insert(SeckillSku seckillSku);

    SeckillSku selectById(Long id);

    SeckillSku selectByActivityAndSku(@Param("activityId") Long activityId,
                                     @Param("skuId") Long skuId);

    List<SeckillSku> findByActivityId(Long activityId);

    List<SeckillSku> findActiveByActivityId(Long activityId);

    int deleteDraftByActivityId(Long activityId);

    int updateStatusByActivity(@Param("activityId") Long activityId,
                               @Param("expectedStatus") Integer expectedStatus,
                               @Param("targetStatus") Integer targetStatus);

    int countOutstandingRequests(Long activityId);
}
