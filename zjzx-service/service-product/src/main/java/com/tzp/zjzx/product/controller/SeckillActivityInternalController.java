package com.tzp.zjzx.product.controller;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.common.security.InternalApiAuth;
import com.tzp.zjzx.model.dto.seckill.SeckillActivityCreateDto;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.seckill.SeckillActivityAdminVo;
import com.tzp.zjzx.product.service.SeckillActivityService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/product/internal/seckill")
public class SeckillActivityInternalController {

    private final SeckillActivityService activityService;

    @Value("${zjzx.internal-api.token}")
    private String internalApiToken;

    public SeckillActivityInternalController(SeckillActivityService activityService) {
        this.activityService = activityService;
    }

    @PostMapping("/activities")
    public Result<Long> create(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String token,
            @Valid @RequestBody SeckillActivityCreateDto dto) {
        InternalApiAuth.verify(internalApiToken, token);
        return Result.build(activityService.create(dto), ResultCodeEnum.SUCCESS);
    }

    @GetMapping("/activities/{activityId}")
    public Result<SeckillActivityAdminVo> get(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String token,
            @PathVariable Long activityId) {
        InternalApiAuth.verify(internalApiToken, token);
        return Result.build(activityService.getAdminById(activityId), ResultCodeEnum.SUCCESS);
    }

    @GetMapping("/activities")
    public Result<PageInfo<SeckillActivityAdminVo>> list(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String token,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer limit,
            @RequestParam(required = false) Integer status) {
        InternalApiAuth.verify(internalApiToken, token);
        return Result.build(
                activityService.findAdminPage(page, limit, status),
                ResultCodeEnum.SUCCESS);
    }

    @PutMapping("/activities/{activityId}")
    public Result<Void> update(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String token,
            @PathVariable Long activityId,
            @Valid @RequestBody SeckillActivityCreateDto dto) {
        InternalApiAuth.verify(internalApiToken, token);
        activityService.update(activityId, dto);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    @PostMapping("/activities/{activityId}/publish")
    public Result<Void> publish(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String token,
            @PathVariable Long activityId) {
        InternalApiAuth.verify(internalApiToken, token);
        activityService.publish(activityId);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    @PostMapping("/activities/{activityId}/offline")
    public Result<Void> offline(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String token,
            @PathVariable Long activityId) {
        InternalApiAuth.verify(internalApiToken, token);
        activityService.offline(activityId);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }
}
