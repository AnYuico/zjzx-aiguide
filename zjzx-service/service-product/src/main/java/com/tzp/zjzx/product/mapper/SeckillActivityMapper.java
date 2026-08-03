package com.tzp.zjzx.product.mapper;

import com.tzp.zjzx.model.entity.seckill.SeckillActivity;
import com.tzp.zjzx.model.vo.seckill.SeckillActivityAdminVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SeckillActivityMapper {

    int insert(SeckillActivity activity);

    SeckillActivity selectById(Long id);

    List<SeckillActivity> findPublished();

    List<SeckillActivityAdminVo> findAdmin(@Param("status") Integer status);

    List<SeckillActivity> findEndingCandidates();

    int updateDraft(@Param("id") Long id,
                    @Param("name") String name,
                    @Param("startTime") java.util.Date startTime,
                    @Param("endTime") java.util.Date endTime);

    int updateStatus(@Param("id") Long id,
                     @Param("expectedStatus") Integer expectedStatus,
                     @Param("targetStatus") Integer targetStatus);
}
