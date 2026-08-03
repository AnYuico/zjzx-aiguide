package com.tzp.zjzx.product.controller;

import com.tzp.zjzx.model.vo.product.CategoryVo;
import com.tzp.zjzx.model.vo.product.ProductSkuVo;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.h5.IndexVo;
import com.tzp.zjzx.product.service.CategoryService;
import com.tzp.zjzx.product.service.ProductService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "首页接口管理")
@RestController
@RequestMapping("/api/product/index")
//@CrossOrigin //跨域
public class IndexController {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private ProductService productService;

    /**
     * 首页
     * @return
     */
    @GetMapping
    public Result index(){
        //1 获取所有一级分类
        List<CategoryVo> categoryList = categoryService.selectOneCategory();

        //2 根据销量排行 获取前十条数据
        List<ProductSkuVo> productSkuList = productService.selectProductSkuBySale();

        //3 封装数据
        IndexVo indexVo = new IndexVo();
        indexVo.setCategoryList(categoryList);
        indexVo.setProductSkuList(productSkuList);

        return Result.build(indexVo, ResultCodeEnum.SUCCESS);
    }
}
