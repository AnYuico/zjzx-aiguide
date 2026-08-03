package com.tzp.zjzx.product.service;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.entity.seckill.SeckillActivity;
import com.tzp.zjzx.model.entity.seckill.SeckillSku;
import com.tzp.zjzx.model.enums.SeckillActivityStatus;
import com.tzp.zjzx.model.enums.SeckillSkuStatus;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.product.mapper.SeckillActivityMapper;
import com.tzp.zjzx.product.mapper.SeckillSkuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.Date;
import java.util.List;

@Service
public class SeckillActivityLifecycleService {

    private final SeckillActivityMapper activityMapper;
    private final SeckillSkuMapper skuMapper;

    public SeckillActivityLifecycleService(SeckillActivityMapper activityMapper,
                                           SeckillSkuMapper skuMapper) {
        this.activityMapper = activityMapper;
        this.skuMapper = skuMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public SeckillActivity beginPreheat(Long activityId) {
        SeckillActivity activity = requireActivity(activityId);
        if (Integer.valueOf(SeckillActivityStatus.PREHEATING.getCode())
                .equals(activity.getStatus())) {
            return activity;
        }
        if (!Integer.valueOf(SeckillActivityStatus.DRAFT.getCode())
                .equals(activity.getStatus())
                || activity.getEndTime() == null
                || !activity.getEndTime().after(new Date())) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_STATE_ERROR);
        }
        if (activityMapper.updateStatus(activityId,
                SeckillActivityStatus.DRAFT.getCode(),
                SeckillActivityStatus.PREHEATING.getCode()) != 1) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_STATE_ERROR);
        }
        activity.setStatus(SeckillActivityStatus.PREHEATING.getCode());
        return activity;
    }

    @Transactional(rollbackFor = Exception.class)
    public void completePublish(Long activityId) {
        List<SeckillSku> skus = skuMapper.findByActivityId(activityId);
        if (CollectionUtils.isEmpty(skus)
                || skuMapper.updateStatusByActivity(activityId,
                SeckillSkuStatus.DRAFT.getCode(),
                SeckillSkuStatus.ACTIVE.getCode()) != skus.size()
                || activityMapper.updateStatus(activityId,
                SeckillActivityStatus.PREHEATING.getCode(),
                SeckillActivityStatus.PUBLISHED.getCode()) != 1) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_STATE_ERROR);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void beginEnding(Long activityId) {
        SeckillActivity activity = requireActivity(activityId);
        if (Integer.valueOf(SeckillActivityStatus.ENDING.getCode())
                .equals(activity.getStatus())
                || Integer.valueOf(SeckillActivityStatus.ENDED.getCode())
                .equals(activity.getStatus())) {
            return;
        }
        int expectedStatus = activity.getStatus();
        if (expectedStatus != SeckillActivityStatus.PREHEATING.getCode()
                && expectedStatus != SeckillActivityStatus.PUBLISHED.getCode()) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_STATE_ERROR);
        }
        if (activityMapper.updateStatus(activityId, expectedStatus,
                SeckillActivityStatus.ENDING.getCode()) != 1) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_STATE_ERROR);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void finishDraft(Long activityId) {
        SeckillActivity activity = requireActivity(activityId);
        if (Integer.valueOf(SeckillActivityStatus.ENDED.getCode())
                .equals(activity.getStatus())) {
            return;
        }
        if (!Integer.valueOf(SeckillActivityStatus.DRAFT.getCode())
                .equals(activity.getStatus())) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_STATE_ERROR);
        }
        List<SeckillSku> skus = skuMapper.findByActivityId(activityId);
        if (CollectionUtils.isEmpty(skus)
                || skuMapper.updateStatusByActivity(activityId,
                SeckillSkuStatus.DRAFT.getCode(),
                SeckillSkuStatus.ENDED.getCode()) != skus.size()
                || activityMapper.updateStatus(activityId,
                SeckillActivityStatus.DRAFT.getCode(),
                SeckillActivityStatus.ENDED.getCode()) != 1) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_STATE_ERROR);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void finish(Long activityId) {
        skuMapper.updateStatusByActivity(activityId,
                SeckillSkuStatus.DRAFT.getCode(), SeckillSkuStatus.ENDED.getCode());
        skuMapper.updateStatusByActivity(activityId,
                SeckillSkuStatus.ACTIVE.getCode(), SeckillSkuStatus.ENDED.getCode());
        if (activityMapper.updateStatus(activityId,
                SeckillActivityStatus.ENDING.getCode(),
                SeckillActivityStatus.ENDED.getCode()) != 1) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_STATE_ERROR);
        }
    }

    private SeckillActivity requireActivity(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_FOUND);
        }
        return activity;
    }
}
