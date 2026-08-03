package com.tzp.zjzx.product.controller;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.model.dto.h5.ProductSkuDto;
import com.tzp.zjzx.model.dto.internal.ProductSkuInternalDto;
import com.tzp.zjzx.model.dto.product.ProductDto;
import com.tzp.zjzx.model.dto.product.StockReserveRequest;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.h5.ProductItemVo;
import com.tzp.zjzx.model.vo.product.ProductSkuVo;
import com.tzp.zjzx.product.service.ProductService;
import com.tzp.zjzx.product.service.InventoryService;
import com.tzp.zjzx.common.security.InternalApiAuth;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Value;

@RestController
@RequestMapping("/api/product")
public class ProductController {

    @Autowired
    private ProductService productService;

    @Autowired
    private InventoryService inventoryService;

    @Value("${zjzx.internal-api.token}")
    private String internalApiToken;

    @Operation(summary = "分页查询")
    @GetMapping("/{page}/{limit}")
    public Result list(@PathVariable Integer page,
                       @PathVariable Integer limit,
                       ProductSkuDto productSkuDto) {
        PageInfo<ProductSkuVo> pageInfo =
                productService.findByPage(page, limit, productSkuDto);
        return Result.build(pageInfo, ResultCodeEnum.SUCCESS);
    }

    @Operation(summary = "商品详情")
    @GetMapping("item/{skuId}")
    public Result item(@PathVariable Long skuId){
        ProductItemVo productItemVo = productService.item(skuId);
        return Result.build(productItemVo,ResultCodeEnum.SUCCESS);
    }

    //远程调用: 根据skuId返回sku信息
    @GetMapping("internal/sku/{skuId}")
    public Result<ProductSkuInternalDto> getBySkuId(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String token,
            @PathVariable Long skuId){
        InternalApiAuth.verify(internalApiToken, token);
        return Result.build(productService.getInternalSku(skuId), ResultCodeEnum.SUCCESS);
    }

    @PostMapping("internal/inventory/reserve")
    public Result<Boolean> reserveStock(@RequestHeader(InternalApiAuth.HEADER_NAME) String token,
                                        @RequestBody StockReserveRequest request) {
        InternalApiAuth.verify(internalApiToken, token);
        inventoryService.reserveStock(request);
        return Result.build(true, ResultCodeEnum.SUCCESS);
    }

    @PostMapping("internal/inventory/confirm/{orderNo}")
    public Result<Boolean> confirmStock(@RequestHeader(InternalApiAuth.HEADER_NAME) String token,
                                        @PathVariable String orderNo) {
        InternalApiAuth.verify(internalApiToken, token);
        inventoryService.confirmStock(orderNo);
        return Result.build(true, ResultCodeEnum.SUCCESS);
    }

    @PostMapping("internal/inventory/release/{orderNo}")
    public Result<Boolean> releaseStock(@RequestHeader(InternalApiAuth.HEADER_NAME) String token,
                                        @PathVariable String orderNo) {
        InternalApiAuth.verify(internalApiToken, token);
        inventoryService.releaseStock(orderNo);
        return Result.build(true, ResultCodeEnum.SUCCESS);
    }
}
