package com.tzp.zjzx.manager.controller;


import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.manager.service.ProductService;
import com.tzp.zjzx.model.dto.product.ProductCreateDto;
import com.tzp.zjzx.model.dto.product.ProductDto;
import com.tzp.zjzx.model.dto.product.ProductUpdateDto;
import com.tzp.zjzx.model.entity.product.Product;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin/product/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    /**
     * 上下架状态更新
     * @param id
     * @param status
     * @return
     */
    @GetMapping("/updateStatus/{id}/{status}")
    public Result updateStatus(@PathVariable Long id, @PathVariable Integer status) {
        productService.updateStatus(id, status);
        return Result.build(null , ResultCodeEnum.SUCCESS) ;
    }

    /**
     * 商品审核状态更新
     * @param id
     * @param auditStatus
     * @return
     */
    @GetMapping("/updateAuditStatus/{id}/{auditStatus}")
    public Result updateAuditStatus(@PathVariable Long id, @PathVariable Integer auditStatus) {
        productService.updateAuditStatus(id, auditStatus);
        return Result.build(null , ResultCodeEnum.SUCCESS) ;
    }


    /**
     * 根据商品id删除商品信息
     * @param id
     * @return
     */
    @DeleteMapping("/deleteById/{id}")
    public Result deleteById(@Parameter(name = "id", description = "商品id", required = true) @PathVariable Long id) {
        productService.deleteById(id);
        return Result.build(null , ResultCodeEnum.SUCCESS) ;
    }


    /**
     * 根据商品id修改商品信息
     * @param productUpdateDto
     * @return
     */
    @PutMapping("/updateById")
    public Result updateById(@Parameter(name = "product", description = "商品修改请求", required = true)
                             @Valid @RequestBody ProductUpdateDto productUpdateDto) {
        productService.updateById(productUpdateDto);
        return Result.build(null , ResultCodeEnum.SUCCESS) ;
    }


    /**
     * 根据商品id查询商品信息
     * @param id
     * @return
     */
    @GetMapping("/getById/{id}")
    public Result getById(@PathVariable("id") Long id){
        Product product = productService.getById(id);
        return Result.build(product,ResultCodeEnum.SUCCESS);
    }



    /**
     * 列表 条件查询接口
     * @param page
     * @param limit
     * @param productDto
     * @return
     */
    @GetMapping("/{page}/{limit}")
    public Result list(@PathVariable("page") Integer page,
                       @PathVariable("limit") Integer limit,
                       ProductDto productDto){

        PageInfo<Product> pageInfo = productService.findByPage(page,limit,productDto);

        return Result.build(pageInfo, ResultCodeEnum.SUCCESS);
    }

    /**
     * 添加商品
     * @param productCreateDto
     * @return
     */
    @PostMapping("save")
    public Result save(@Valid @RequestBody ProductCreateDto productCreateDto){
        productService.save(productCreateDto);
        return Result.build(null,ResultCodeEnum.SUCCESS);
    }
}
