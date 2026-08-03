package com.tzp.zjzx.manager.controller;

import com.tzp.zjzx.manager.service.ProductUnitService;
import com.tzp.zjzx.model.entity.base.ProductUnit;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/admin/product/productUnit")
public class ProductUnitController {

    @Autowired
    private ProductUnitService productUnitService;

    /**
     * 查询所有
     * @return
     */
    @GetMapping("/findAll")
    public Result<List<ProductUnit>> findAll(){
        return Result.build(productUnitService.findAll(), ResultCodeEnum.SUCCESS);
    }
}
