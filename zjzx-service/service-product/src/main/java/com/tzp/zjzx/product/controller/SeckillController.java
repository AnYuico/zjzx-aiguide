package com.tzp.zjzx.product.controller;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.dto.seckill.SeckillSubmitDto;
import com.tzp.zjzx.model.entity.user.UserInfo;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.seckill.SeckillActivityVo;
import com.tzp.zjzx.model.vo.seckill.SeckillResultVo;
import com.tzp.zjzx.model.vo.seckill.SeckillSubmitVo;
import com.tzp.zjzx.product.service.SeckillActivityService;
import com.tzp.zjzx.product.service.SeckillSubmissionService;
import com.tzp.zjzx.utils.AuthContextUtil;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/product/seckill")
public class SeckillController {

    private final SeckillActivityService activityService;
    private final SeckillSubmissionService submissionService;

    public SeckillController(SeckillActivityService activityService,
                             SeckillSubmissionService submissionService) {
        this.activityService = activityService;
        this.submissionService = submissionService;
    }

    @GetMapping("/activities")
    public Result<List<SeckillActivityVo>> listActivities() {
        return Result.build(activityService.listPublished(), ResultCodeEnum.SUCCESS);
    }

    @GetMapping("/activity/{activityId}")
    public Result<SeckillActivityVo> getActivity(@PathVariable Long activityId) {
        return Result.build(activityService.getById(activityId), ResultCodeEnum.SUCCESS);
    }

    @PostMapping("/auth/activity/{activityId}/sku/{skuId}/submit")
    public Result<SeckillSubmitVo> submit(@PathVariable Long activityId,
                                         @PathVariable Long skuId,
                                         @Valid @RequestBody SeckillSubmitDto dto) {
        UserInfo user = requireUser();
        return Result.build(submissionService.submit(
                activityId, skuId, user.getId(), dto), ResultCodeEnum.SUCCESS);
    }

    @GetMapping("/auth/activity/{activityId}/sku/{skuId}/result/{requestId}")
    public Result<SeckillResultVo> result(@PathVariable Long activityId,
                                         @PathVariable Long skuId,
                                         @PathVariable String requestId) {
        UserInfo user = requireUser();
        return Result.build(submissionService.getResult(
                activityId, skuId, user.getId(), requestId), ResultCodeEnum.SUCCESS);
    }

    private UserInfo requireUser() {
        UserInfo user = AuthContextUtil.getUserInfo();
        if (user == null || user.getId() == null) {
            throw new MyException(ResultCodeEnum.LOGIN_AUTH);
        }
        return user;
    }
}

