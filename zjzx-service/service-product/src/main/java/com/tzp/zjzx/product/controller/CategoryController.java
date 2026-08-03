package com.tzp.zjzx.product.controller;

import com.tzp.zjzx.model.vo.product.CategoryVo;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.product.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;


//@CrossOrigin //跨域
@RestController
@RequestMapping("/api/product/category")
public class CategoryController {

    @Autowired
    private CategoryService categoryService;

    /**
     * 查询所有分类 树形封装
     * @return
     */
    @GetMapping("/findCategoryTree")
    public Result findCategoryTree(){
        List<CategoryVo> categoryList = categoryService.findCategoryTree();
        return Result.build(categoryList, ResultCodeEnum.SUCCESS);
    }

}
