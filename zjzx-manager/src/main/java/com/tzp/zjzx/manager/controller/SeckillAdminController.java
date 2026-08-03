package com.tzp.zjzx.manager.controller;

import com.tzp.zjzx.manager.client.ProductSeckillAdminClient;
import com.tzp.zjzx.model.dto.seckill.SeckillActivityCreateDto;
import com.tzp.zjzx.model.vo.common.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/product/seckill")
public class SeckillAdminController {

    private final ProductSeckillAdminClient seckillClient;

    public SeckillAdminController(ProductSeckillAdminClient seckillClient) {
        this.seckillClient = seckillClient;
    }

    @GetMapping("/{page}/{limit}")
    public Result<?> list(
            @PathVariable Integer page,
            @PathVariable Integer limit,
            @RequestParam(required = false) Integer status) {
        return seckillClient.list(page, limit, status);
    }

    @GetMapping("/getById/{activityId}")
    public Result<?> getById(@PathVariable Long activityId) {
        return seckillClient.getById(activityId);
    }

    @PostMapping("/save")
    public Result<?> create(@Valid @RequestBody SeckillActivityCreateDto dto) {
        return seckillClient.create(dto);
    }

    @PutMapping("/updateById/{activityId}")
    public Result<?> update(
            @PathVariable Long activityId,
            @Valid @RequestBody SeckillActivityCreateDto dto) {
        return seckillClient.update(activityId, dto);
    }

    @PostMapping("/publish/{activityId}")
    public Result<?> publish(@PathVariable Long activityId) {
        return seckillClient.publish(activityId);
    }

    @PostMapping("/offline/{activityId}")
    public Result<?> offline(@PathVariable Long activityId) {
        return seckillClient.offline(activityId);
    }
}
