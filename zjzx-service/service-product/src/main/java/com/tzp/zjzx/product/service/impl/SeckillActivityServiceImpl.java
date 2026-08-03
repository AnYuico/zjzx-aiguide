package com.tzp.zjzx.product.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.dto.seckill.SeckillActivityCreateDto;
import com.tzp.zjzx.model.dto.seckill.SeckillSkuCreateDto;
import com.tzp.zjzx.model.entity.product.ProductSku;
import com.tzp.zjzx.model.entity.seckill.SeckillActivity;
import com.tzp.zjzx.model.entity.seckill.SeckillSku;
import com.tzp.zjzx.model.enums.SeckillActivityStatus;
import com.tzp.zjzx.model.enums.SeckillSkuStatus;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.seckill.SeckillActivityAdminVo;
import com.tzp.zjzx.model.vo.seckill.SeckillActivityVo;
import com.tzp.zjzx.model.vo.seckill.SeckillSkuAdminVo;
import com.tzp.zjzx.model.vo.seckill.SeckillSkuVo;
import com.tzp.zjzx.product.mapper.ProductSkuMapper;
import com.tzp.zjzx.product.mapper.SeckillActivityMapper;
import com.tzp.zjzx.product.mapper.SeckillSkuMapper;
import com.tzp.zjzx.product.service.SeckillActivityService;
import com.tzp.zjzx.product.service.SeckillActivityLifecycleService;
import com.tzp.zjzx.product.service.SeckillRedisService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class SeckillActivityServiceImpl implements SeckillActivityService {

    private static final int MAX_ACTIVITY_SKUS = 100;

    private final SeckillActivityMapper activityMapper;
    private final SeckillSkuMapper seckillSkuMapper;
    private final ProductSkuMapper productSkuMapper;
    private final SeckillActivityLifecycleService lifecycleService;
    private final SeckillRedisService redisService;

    public SeckillActivityServiceImpl(SeckillActivityMapper activityMapper,
                                      SeckillSkuMapper seckillSkuMapper,
                                      ProductSkuMapper productSkuMapper,
                                      SeckillActivityLifecycleService lifecycleService,
                                      SeckillRedisService redisService) {
        this.activityMapper = activityMapper;
        this.seckillSkuMapper = seckillSkuMapper;
        this.productSkuMapper = productSkuMapper;
        this.lifecycleService = lifecycleService;
        this.redisService = redisService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SeckillActivityCreateDto dto) {
        validateActivity(dto);

        SeckillActivity activity = new SeckillActivity();
        activity.setName(dto.getName().trim());
        activity.setStartTime(dto.getStartTime());
        activity.setEndTime(dto.getEndTime());
        activity.setStatus(SeckillActivityStatus.DRAFT.getCode());
        if (activityMapper.insert(activity) != 1 || activity.getId() == null) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        insertDraftSkus(activity.getId(), dto.getSkuList());
        return activity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long activityId, SeckillActivityCreateDto dto) {
        if (activityId == null || activityId <= 0) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }
        validateActivity(dto);
        if (activityMapper.updateDraft(
                activityId,
                dto.getName().trim(),
                dto.getStartTime(),
                dto.getEndTime()) != 1) {
            if (activityMapper.selectById(activityId) == null) {
                throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_FOUND);
            }
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_STATE_ERROR);
        }
        seckillSkuMapper.deleteDraftByActivityId(activityId);
        insertDraftSkus(activityId, dto.getSkuList());
    }

    private void insertDraftSkus(Long activityId, List<SeckillSkuCreateDto> skuDtos) {
        Set<Long> skuIds = new HashSet<>();
        for (SeckillSkuCreateDto skuDto : skuDtos) {
            if (skuDto == null || skuDto.getSkuId() == null
                    || !skuIds.add(skuDto.getSkuId())) {
                throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
            }
            ProductSku productSku = requireSaleableSku(skuDto);
            validateQuota(skuDto, productSku);

            SeckillSku seckillSku = new SeckillSku();
            seckillSku.setActivityId(activityId);
            seckillSku.setSkuId(skuDto.getSkuId());
            seckillSku.setSeckillPrice(skuDto.getSeckillPrice());
            seckillSku.setTotalStock(skuDto.getTotalStock());
            seckillSku.setAvailableStock(skuDto.getTotalStock());
            seckillSku.setLimitPerUser(1);
            seckillSku.setStatus(SeckillSkuStatus.DRAFT.getCode());
            if (seckillSkuMapper.insert(seckillSku) != 1) {
                throw new MyException(ResultCodeEnum.DATA_ERROR);
            }
        }
    }

    @Override
    public void publish(Long activityId) {
        SeckillActivity current = activityMapper.selectById(activityId);
        if (current == null) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_FOUND);
        }
        List<SeckillSku> existingSkus =
                seckillSkuMapper.findByActivityId(activityId);
        if (CollectionUtils.isEmpty(existingSkus)) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_STATE_ERROR);
        }
        if (Integer.valueOf(SeckillActivityStatus.PUBLISHED.getCode())
                .equals(current.getStatus())) {
            activateIfStillPublished(current, existingSkus);
            return;
        }

        SeckillActivity activity = lifecycleService.beginPreheat(activityId);
        redisService.preheat(activity, existingSkus);
        lifecycleService.completePublish(activityId);
        activity.setStatus(SeckillActivityStatus.PUBLISHED.getCode());
        for (SeckillSku sku : existingSkus) {
            sku.setStatus(SeckillSkuStatus.ACTIVE.getCode());
        }
        activateIfStillPublished(activity, existingSkus);
    }

    private void activateIfStillPublished(
            SeckillActivity activity, List<SeckillSku> skus) {
        redisService.activate(activity, skus);
        SeckillActivity latest = activityMapper.selectById(activity.getId());
        if (latest == null || !Integer.valueOf(SeckillActivityStatus.PUBLISHED.getCode())
                .equals(latest.getStatus())) {
            redisService.deactivate(activity.getId(), skus);
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_STATE_ERROR);
        }
    }

    @Override
    public void offline(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_FOUND);
        }
        int status = activity.getStatus();
        if (status == SeckillActivityStatus.ENDED.getCode()
                || status == SeckillActivityStatus.ENDING.getCode()) {
            return;
        }
        if (status == SeckillActivityStatus.DRAFT.getCode()) {
            lifecycleService.finishDraft(activityId);
            return;
        }
        if (status != SeckillActivityStatus.PREHEATING.getCode()
                && status != SeckillActivityStatus.PUBLISHED.getCode()) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_STATE_ERROR);
        }
        List<SeckillSku> skus = seckillSkuMapper.findByActivityId(activityId);
        if (CollectionUtils.isEmpty(skus)) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_STATE_ERROR);
        }
        redisService.deactivate(activityId, skus);
        lifecycleService.beginEnding(activityId);
    }

    @Override
    public SeckillActivityVo getById(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_FOUND);
        }
        return toVo(activity, seckillSkuMapper.findByActivityId(activityId));
    }

    @Override
    public SeckillActivityAdminVo getAdminById(Long activityId) {
        SeckillActivity activity = activityMapper.selectById(activityId);
        if (activity == null) {
            throw new MyException(ResultCodeEnum.SECKILL_ACTIVITY_NOT_FOUND);
        }
        return toAdminVo(activity, seckillSkuMapper.findByActivityId(activityId));
    }

    @Override
    public PageInfo<SeckillActivityAdminVo> findAdminPage(
            Integer page, Integer limit, Integer status) {
        if (page == null || page <= 0 || limit == null || limit <= 0 || limit > 100
                || status != null && (status < SeckillActivityStatus.DRAFT.getCode()
                || status > SeckillActivityStatus.ENDED.getCode())) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }
        PageHelper.startPage(page, limit);
        return new PageInfo<>(activityMapper.findAdmin(status));
    }

    @Override
    public List<SeckillActivityVo> listPublished() {
        List<SeckillActivityVo> result = new ArrayList<>();
        for (SeckillActivity activity : activityMapper.findPublished()) {
            result.add(toVo(activity, seckillSkuMapper.findByActivityId(activity.getId())));
        }
        return result;
    }

    private void validateActivity(SeckillActivityCreateDto dto) {
        if (dto == null || !StringUtils.hasText(dto.getName())
                || dto.getName().trim().length() > 100
                || dto.getStartTime() == null || dto.getEndTime() == null
                || !dto.getEndTime().after(dto.getStartTime())
                || !dto.getEndTime().after(new Date())
                || CollectionUtils.isEmpty(dto.getSkuList())
                || dto.getSkuList().size() > MAX_ACTIVITY_SKUS) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }
    }

    private ProductSku requireSaleableSku(SeckillSkuCreateDto skuDto) {
        if (skuDto.getSeckillPrice() == null || skuDto.getTotalStock() == null
                || skuDto.getSeckillPrice().compareTo(BigDecimal.ZERO) < 0
                || skuDto.getTotalStock() <= 0) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }
        ProductSku productSku = productSkuMapper.getById(skuDto.getSkuId());
        if (productSku == null || !Integer.valueOf(1).equals(productSku.getStatus())
                || Integer.valueOf(1).equals(productSku.getIsDeleted())) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        return productSku;
    }

    private void validateQuota(SeckillSkuCreateDto skuDto, ProductSku productSku) {
        if (productSku.getSalePrice() == null || productSku.getStockNum() == null
                || skuDto.getSeckillPrice().compareTo(productSku.getSalePrice()) > 0
                || skuDto.getTotalStock() > productSku.getStockNum()) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }
    }

    private SeckillActivityVo toVo(SeckillActivity activity, List<SeckillSku> skus) {
        SeckillActivityVo activityVo = new SeckillActivityVo();
        activityVo.setId(activity.getId());
        activityVo.setName(activity.getName());
        activityVo.setStartTime(activity.getStartTime());
        activityVo.setEndTime(activity.getEndTime());
        activityVo.setStatus(activity.getStatus());

        List<SeckillSkuVo> skuVos = new ArrayList<>();
        for (SeckillSku seckillSku : skus) {
            ProductSku productSku = productSkuMapper.getById(seckillSku.getSkuId());
            SeckillSkuVo skuVo = new SeckillSkuVo();
            skuVo.setId(seckillSku.getId());
            skuVo.setSkuId(seckillSku.getSkuId());
            skuVo.setSeckillPrice(seckillSku.getSeckillPrice());
            skuVo.setAvailableStock(seckillSku.getAvailableStock());
            skuVo.setLimitPerUser(seckillSku.getLimitPerUser());
            if (productSku != null) {
                skuVo.setSkuName(productSku.getSkuName());
                skuVo.setThumbImg(productSku.getThumbImg());
                skuVo.setOriginalPrice(productSku.getSalePrice());
            }
            skuVos.add(skuVo);
        }
        activityVo.setSkuList(skuVos);
        return activityVo;
    }

    private SeckillActivityAdminVo toAdminVo(
            SeckillActivity activity, List<SeckillSku> skus) {
        SeckillActivityAdminVo activityVo = new SeckillActivityAdminVo();
        activityVo.setId(activity.getId());
        activityVo.setName(activity.getName());
        activityVo.setStartTime(activity.getStartTime());
        activityVo.setEndTime(activity.getEndTime());
        activityVo.setStatus(activity.getStatus());
        activityVo.setCreateTime(activity.getCreateTime());
        activityVo.setUpdateTime(activity.getUpdateTime());

        List<SeckillSkuAdminVo> skuVos = new ArrayList<>();
        for (SeckillSku seckillSku : skus) {
            ProductSku productSku = productSkuMapper.getById(seckillSku.getSkuId());
            SeckillSkuAdminVo skuVo = new SeckillSkuAdminVo();
            skuVo.setId(seckillSku.getId());
            skuVo.setSkuId(seckillSku.getSkuId());
            skuVo.setSeckillPrice(seckillSku.getSeckillPrice());
            skuVo.setTotalStock(seckillSku.getTotalStock());
            skuVo.setAvailableStock(seckillSku.getAvailableStock());
            skuVo.setLimitPerUser(seckillSku.getLimitPerUser());
            skuVo.setStatus(seckillSku.getStatus());
            if (productSku != null) {
                skuVo.setSkuName(productSku.getSkuName());
                skuVo.setThumbImg(productSku.getThumbImg());
                skuVo.setOriginalPrice(productSku.getSalePrice());
            }
            skuVos.add(skuVo);
        }
        activityVo.setSkuCount(skuVos.size());
        activityVo.setSkuList(skuVos);
        return activityVo;
    }
}
