package com.tzp.zjzx.product.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.ai.contract.dto.ProductKnowledgePageQueryDto;
import com.tzp.zjzx.ai.contract.dto.ProductGuideQueryDto;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgeDocumentVo;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgePageVo;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.product.mapper.ProductGuideMapper;
import com.tzp.zjzx.product.service.ProductGuideService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Service
public class ProductGuideServiceImpl implements ProductGuideService {

    static final int DEFAULT_LIMIT = 10;
    static final int MAX_LIMIT = 20;
    static final int MAX_KEYWORD_LENGTH = 50;
    static final int DEFAULT_KNOWLEDGE_PAGE_SIZE = 100;
    static final int MAX_KNOWLEDGE_PAGE_SIZE = 500;

    private final ProductGuideMapper productGuideMapper;

    public ProductGuideServiceImpl(ProductGuideMapper productGuideMapper) {
        this.productGuideMapper = productGuideMapper;
    }

    @Override
    public List<ProductGuideVo> search(ProductGuideQueryDto query) {
        String keyword = normalizeKeyword(query == null ? null : query.getKeyword());
        int limit = query == null || query.getLimit() == null
                ? DEFAULT_LIMIT
                : query.getLimit();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        return productGuideMapper.search(keyword, limit);
    }

    @Override
    public ProductGuideVo getBySkuId(Long skuId) {
        if (skuId == null || skuId <= 0) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        ProductGuideVo product = productGuideMapper.getBySkuId(skuId);
        if (product == null) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        return product;
    }

    @Override
    public ProductKnowledgePageVo getKnowledgePage(ProductKnowledgePageQueryDto query) {
        long afterSkuId = query == null || query.getAfterSkuId() == null
                ? 0L
                : query.getAfterSkuId();
        int limit = query == null || query.getLimit() == null
                ? DEFAULT_KNOWLEDGE_PAGE_SIZE
                : query.getLimit();
        if (afterSkuId < 0 || limit < 1 || limit > MAX_KNOWLEDGE_PAGE_SIZE) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }

        List<ProductKnowledgeDocumentVo> rows =
                productGuideMapper.findKnowledgePage(afterSkuId, limit + 1);
        boolean hasMore = rows.size() > limit;
        List<ProductKnowledgeDocumentVo> items = new ArrayList<>(
                rows.subList(0, Math.min(rows.size(), limit))
        );
        items.forEach(item -> item.setContentHash(calculateContentHash(item)));
        Long nextCursor = items.isEmpty()
                ? afterSkuId
                : items.get(items.size() - 1).getSkuId();
        return new ProductKnowledgePageVo(items, nextCursor, hasMore);
    }

    @Override
    public List<ProductKnowledgeDocumentVo> getKnowledgeByProductId(Long productId) {
        if (productId == null || productId <= 0) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        List<ProductKnowledgeDocumentVo> documents =
                productGuideMapper.findKnowledgeByProductId(productId);
        documents.forEach(item -> item.setContentHash(calculateContentHash(item)));
        return documents;
    }

    private String normalizeKeyword(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String normalized = keyword.trim();
        if (normalized.length() > MAX_KEYWORD_LENGTH) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        return normalized;
    }

    private String calculateContentHash(ProductKnowledgeDocumentVo item) {
        String canonicalContent = String.join("\u001F",
                value(item.getProductId()),
                value(item.getSkuId()),
                value(item.getCategoryId()),
                value(item.getBrandId()),
                value(item.getProductName()),
                value(item.getSkuName()),
                value(item.getBrandName()),
                value(item.getCategoryPath()),
                value(item.getProductSpec()),
                value(item.getSkuSpec()),
                value(item.getUnitName())
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonicalContent.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
