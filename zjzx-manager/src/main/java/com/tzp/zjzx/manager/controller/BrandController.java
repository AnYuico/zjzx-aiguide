package com.tzp.zjzx.manager.controller;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.common.log.annotation.Log;
import com.tzp.zjzx.common.log.enums.OperatorType;
import com.tzp.zjzx.manager.service.BrandService;
import com.tzp.zjzx.model.entity.product.Brand;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/product/brand")
public class BrandController {

    @Autowired
    private BrandService brandService;

    /**
     * 品牌分页查询
     *
     * @param page
     * @param limit
     * @return
     */
    @Log(title = "品牌管理:分页查询", businessType = 0, operatorType = OperatorType.OTHER)
    @GetMapping("/{page}/{limit}")
    public Result list(@PathVariable("page") Integer page,
                       @PathVariable("limit") Integer limit) {
        PageInfo<Brand> pageInfo = brandService.findByPage(page, limit);
        return Result.build(pageInfo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 品牌添加
     *
     * @param brand
     * @return
     */
    @PostMapping("/save")
    public Result save(@RequestBody Brand brand) {
        brandService.save(brand);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 品牌修改
     *
     * @param brand
     * @return
     */
    @PutMapping("updateById")
    public Result updateById(@RequestBody Brand brand) {
        brandService.updateById(brand);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 品牌删除
     *
     * @param id
     * @return
     */
    @DeleteMapping("/deleteById/{id}")
    public Result deleteById(@PathVariable Long id) {
        brandService.deleteById(id);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }


    /**
     * 查询所有品牌
     *
     * @return
     */
    @GetMapping("/findAll")
    public Result findAll() {
        List<Brand> list = brandService.findAll();
        return Result.build(list, ResultCodeEnum.SUCCESS);
    }
}
