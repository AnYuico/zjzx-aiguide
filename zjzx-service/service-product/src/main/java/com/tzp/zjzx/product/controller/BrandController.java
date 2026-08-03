package com.tzp.zjzx.product.controller;

import com.tzp.zjzx.model.vo.product.BrandVo;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.product.service.BrandService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/product/brand")
public class BrandController {

    @Autowired
    private BrandService brandService;

    @Operation(summary = "查询所有品牌")
    @GetMapping("/findAll")
    public Result findAll(){
        List<BrandVo> brandList = brandService.findAll();
        return Result.build(brandList, ResultCodeEnum.SUCCESS);
    }

}
