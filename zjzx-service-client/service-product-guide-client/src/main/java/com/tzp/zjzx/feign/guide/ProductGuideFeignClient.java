package com.tzp.zjzx.feign.guide;

import com.tzp.zjzx.ai.contract.dto.ProductGuideQueryDto;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

@FeignClient(
        value = "service-product",
        contextId = "productGuideFeignClient",
        configuration = ProductGuideFeignConfiguration.class
)
public interface ProductGuideFeignClient {

    @PostMapping("/api/product/internal/ai-guide/search")
    List<ProductGuideVo> search(@RequestBody ProductGuideQueryDto query);

    @GetMapping("/api/product/internal/ai-guide/sku/{skuId}")
    ProductGuideVo getBySkuId(@PathVariable("skuId") Long skuId);
}
