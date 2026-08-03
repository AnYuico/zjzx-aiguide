package com.tzp.zjzx.user.controller;

import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.h5.RegionVo;
import com.tzp.zjzx.user.service.RegionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "地区级联接口")
@RestController
@RequestMapping("/api/user/region")
public class RegionController {

    private final RegionService regionService;

    public RegionController(RegionService regionService) {
        this.regionService = regionService;
    }

    @Operation(summary = "按父级编码查询下级地区，省级查询传0")
    @GetMapping("children/{parentCode}")
    public Result<List<RegionVo>> findChildren(@PathVariable String parentCode) {
        return Result.build(regionService.findChildren(parentCode), ResultCodeEnum.SUCCESS);
    }
}
