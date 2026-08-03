package com.tzp.zjzx.feign.product;

import com.tzp.zjzx.model.dto.product.StockReserveRequest;
import com.tzp.zjzx.model.dto.internal.ProductSkuInternalDto;
import com.tzp.zjzx.model.vo.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(value = "service-product")
public interface ProductFeignClient {

    /**
     * 根据skuId查询商品sku信息
     * @param skuId
     * @return
     */
    @GetMapping("/api/product/internal/sku/{skuId}")
    Result<ProductSkuInternalDto> getBySkuId(
            @RequestHeader("X-Internal-Token") String internalToken,
            @PathVariable("skuId") Long skuId);

    @PostMapping("/api/product/internal/inventory/reserve")
    Result<Boolean> reserveStock(@RequestHeader("X-Internal-Token") String internalToken,
                                 @RequestBody StockReserveRequest request);

    @PostMapping("/api/product/internal/inventory/confirm/{orderNo}")
    Result<Boolean> confirmStock(@RequestHeader("X-Internal-Token") String internalToken,
                                 @PathVariable("orderNo") String orderNo);

    @PostMapping("/api/product/internal/inventory/release/{orderNo}")
    Result<Boolean> releaseStock(@RequestHeader("X-Internal-Token") String internalToken,
                                 @PathVariable("orderNo") String orderNo);

}
