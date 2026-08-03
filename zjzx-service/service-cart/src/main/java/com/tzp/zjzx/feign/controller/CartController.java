package com.tzp.zjzx.feign.controller;

import com.tzp.zjzx.feign.service.CartService;
import com.tzp.zjzx.common.security.InternalApiAuth;
import com.tzp.zjzx.model.dto.internal.CartItemInternalDto;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.h5.CartItemVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("api/order/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    @Value("${zjzx.internal-api.token}")
    private String internalApiToken;

    @Operation(summary = "添加购物车")
    @GetMapping("auth/addToCart/{skuId}/{skuNum}")
    public Result addToCart(@PathVariable  Long skuId,
                            @PathVariable Integer skuNum) {
        cartService.addToCart(skuId, skuNum);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    @Operation(summary = "查询购物车")
    @GetMapping("auth/cartList")
    public Result<List<CartItemVo>> cartList() {
        List<CartItemVo> cartInfoList = cartService.getCartList();
        return Result.build(cartInfoList, ResultCodeEnum.SUCCESS);
    }


    @Operation(summary = "删除购物车商品")
    @DeleteMapping("auth/deleteCart/{skuId}")
    public Result deleteCart(@PathVariable("skuId") Long skuId) {
        cartService.deleteCart(skuId);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    @Operation(summary="更新购物车商品选中状态")
    @GetMapping("/auth/checkCart/{skuId}/{isChecked}")
    public Result checkCart(@Parameter(name = "skuId", description = "商品skuId", required = true) @PathVariable(value = "skuId") Long skuId,
                            @Parameter(name = "isChecked", description = "是否选中 1:选中 0:取消选中", required = true) @PathVariable(value = "isChecked") Integer isChecked) {
        cartService.checkCart(skuId, isChecked);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    @Operation(summary="更新购物车商品全部选中状态")
    @GetMapping("/auth/allCheckCart/{isChecked}")
    public Result allCheckCart(@Parameter(name = "isChecked", description = "是否选中 1:选中 0:取消选中", required = true) @PathVariable(value = "isChecked") Integer isChecked){
        cartService.allCheckCart(isChecked);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    @Operation(summary="清空购物车")
    @GetMapping("/auth/clearCart")
    public Result clearCart(){
        cartService.clearCart();
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    @Operation(summary="选中的购物车")
    @GetMapping(value = "/internal/checked/{userId}")
    public List<CartItemInternalDto> getAllChecked(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String token,
            @PathVariable Long userId) {
        InternalApiAuth.verify(internalApiToken, token);
        return cartService.getAllChecked(userId);
    }

    /**
     * 删除购物车中选中的商品
     * @return
     */
    @GetMapping("auth/deleteChecked")
    public Result deleteChecked() {
        cartService.deleteChecked();
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

}
