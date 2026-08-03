package com.tzp.zjzx.product.service;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.model.dto.seckill.SeckillActivityCreateDto;
import com.tzp.zjzx.model.vo.seckill.SeckillActivityAdminVo;
import com.tzp.zjzx.model.vo.seckill.SeckillActivityVo;

import java.util.List;

public interface SeckillActivityService {

    Long create(SeckillActivityCreateDto dto);

    void update(Long activityId, SeckillActivityCreateDto dto);

    void publish(Long activityId);

    void offline(Long activityId);

    SeckillActivityVo getById(Long activityId);

    SeckillActivityAdminVo getAdminById(Long activityId);

    PageInfo<SeckillActivityAdminVo> findAdminPage(
            Integer page, Integer limit, Integer status);

    List<SeckillActivityVo> listPublished();
}
