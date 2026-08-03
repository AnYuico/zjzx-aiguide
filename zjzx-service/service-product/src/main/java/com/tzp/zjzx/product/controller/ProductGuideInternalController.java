package com.tzp.zjzx.product.controller;

import com.tzp.zjzx.common.security.InternalApiAuth;
import com.tzp.zjzx.ai.contract.dto.ProductKnowledgePageQueryDto;
import com.tzp.zjzx.ai.contract.dto.ProductGuideQueryDto;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgeDocumentVo;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgePageVo;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import com.tzp.zjzx.product.service.ProductGuideService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/product/internal/ai-guide")
public class ProductGuideInternalController {

    private final ProductGuideService productGuideService;

    @Value("${zjzx.internal-api.token}")
    private String internalApiToken;

    public ProductGuideInternalController(ProductGuideService productGuideService) {
        this.productGuideService = productGuideService;
    }

    @PostMapping("/search")
    public List<ProductGuideVo> search(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String token,
            @RequestBody ProductGuideQueryDto query) {
        InternalApiAuth.verify(internalApiToken, token);
        return productGuideService.search(query);
    }

    @GetMapping("/sku/{skuId}")
    public ProductGuideVo getBySkuId(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String token,
            @PathVariable Long skuId) {
        InternalApiAuth.verify(internalApiToken, token);
        return productGuideService.getBySkuId(skuId);
    }

    @PostMapping("/knowledge/page")
    public ProductKnowledgePageVo getKnowledgePage(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String token,
            @RequestBody(required = false) ProductKnowledgePageQueryDto query) {
        InternalApiAuth.verify(internalApiToken, token);
        return productGuideService.getKnowledgePage(query);
    }

    @GetMapping("/knowledge/product/{productId}")
    public List<ProductKnowledgeDocumentVo> getKnowledgeByProductId(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String token,
            @PathVariable Long productId) {
        InternalApiAuth.verify(internalApiToken, token);
        return productGuideService.getKnowledgeByProductId(productId);
    }
}
