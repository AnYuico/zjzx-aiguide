package com.tzp.zjzx.feign;

import com.tzp.zjzx.model.dto.internal.CartItemInternalDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

import java.util.List;

@FeignClient(value = "service-cart")
public interface CartFeignClient {

    /**
     * 查询购物车中选中的商品
     * @return
     */
    @GetMapping(value = "/api/order/cart/internal/checked/{userId}")
    List<CartItemInternalDto> getAllChecked(
            @RequestHeader("X-Internal-Token") String internalToken,
            @PathVariable("userId") Long userId);

}
