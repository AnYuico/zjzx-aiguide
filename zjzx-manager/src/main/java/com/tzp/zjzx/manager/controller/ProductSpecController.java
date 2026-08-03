package com.tzp.zjzx.manager.controller;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.manager.service.ProductSpecService;
import com.tzp.zjzx.model.entity.product.ProductSpec;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/product/productSpec")
public class ProductSpecController {

    @Autowired
    private ProductSpecService productSpecService;

    /**
     * 分页查询
     * @param page
     * @param limit
     * @return
     */
    @GetMapping("/{page}/{limit}")
    public Result findByPage(@PathVariable("page") Integer page,
                             @PathVariable("limit") Integer limit){

        PageInfo<ProductSpec> pageInfo = productSpecService.findByPage(page, limit);

        return Result.build(pageInfo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 保存商品规格
     * @param productSpec
     * @return
     */
    @PostMapping("/save")
    public Result save(@RequestBody ProductSpec productSpec) {
        productSpecService.save(productSpec);
        return Result.build(null , ResultCodeEnum.SUCCESS) ;
    }

    /**
     * 修改商品规格
     * @param productSpec
     * @return
     */
    @PutMapping("updateById")
    public Result updateById(@RequestBody ProductSpec productSpec) {
        productSpecService.updateById(productSpec);
        return Result.build(null , ResultCodeEnum.SUCCESS) ;
    }

    /**
     * 删除商品规格
     * @param id
     * @return
     */
    @DeleteMapping("/deleteById/{id}")
    public Result removeById(@PathVariable Long id) {
        productSpecService.deleteById(id);
        return Result.build(null , ResultCodeEnum.SUCCESS) ;
    }


    /**
     * 查询商品的所有规格
     * @return
     */
    @GetMapping("findAll")
    public Result findAll() {
        List<ProductSpec> list = productSpecService.findAll();
        return Result.build(list , ResultCodeEnum.SUCCESS) ;
    }
}
