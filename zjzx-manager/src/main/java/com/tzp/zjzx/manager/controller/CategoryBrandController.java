package com.tzp.zjzx.manager.controller;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.manager.service.CategoryBrandService;
import com.tzp.zjzx.model.dto.product.CategoryBrandDto;
import com.tzp.zjzx.model.entity.product.Brand;
import com.tzp.zjzx.model.entity.product.CategoryBrand;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("admin/product/categoryBrand")
public class CategoryBrandController {

    @Autowired
    private CategoryBrandService categoryBrandService;

    //根据分类id查询对应品牌数据
    @GetMapping("/findBrandByCategoryId/{categoryId}")
    public Result findBrandByCategoryId(@PathVariable("categoryId") Long categoryId) {
        List<Brand> list = categoryBrandService.findBrandByCategoryId(categoryId);
        return Result.build(list, ResultCodeEnum.SUCCESS);
    }


    /**
     * 分类品牌分页查询
     *
     * @param page
     * @param limit
     * @param categoryBrandDto
     * @return
     */
    @GetMapping("/{page}/{limit}")
    public Result findByPage(@PathVariable("page") Integer page,
                             @PathVariable("limit") Integer limit,
                             CategoryBrandDto categoryBrandDto) {
        PageInfo<CategoryBrand> pageInfo =
                categoryBrandService.findByPage(page, limit, categoryBrandDto);
        return Result.build(pageInfo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 分类品牌的添加
     *
     * @param categoryBrand
     * @return
     */
    @PostMapping("/save")
    public Result save(@RequestBody CategoryBrand categoryBrand) {
        categoryBrandService.save(categoryBrand);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 分类品牌的修改
     *
     * @param categoryBrand
     * @return
     */
    @PutMapping("updateById")
    public Result updateById(@RequestBody CategoryBrand categoryBrand) {
        categoryBrandService.updateById(categoryBrand);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    /**
     * 删除分类品牌
     *
     * @param id
     * @return
     */
    @DeleteMapping("/deleteById/{id}")
    public Result deleteById(@PathVariable Long id) {
        categoryBrandService.deleteById(id);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }
}
