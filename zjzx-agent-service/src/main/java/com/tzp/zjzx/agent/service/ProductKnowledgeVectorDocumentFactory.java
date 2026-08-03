package com.tzp.zjzx.agent.service;

import com.tzp.zjzx.ai.contract.vo.ProductKnowledgeDocumentVo;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ProductKnowledgeVectorDocumentFactory {

    static final String DOCUMENT_TYPE = "product";
    private static final int MAX_TEXT_FIELD_LENGTH = 2000;

    public Document create(ProductKnowledgeDocumentVo source,
                           String generation) {
        if (source == null || source.getSkuId() == null
                || source.getSkuId() <= 0) {
            throw new IllegalStateException(
                    "Product knowledge document has no valid SKU ID"
            );
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("documentType", DOCUMENT_TYPE);
        metadata.put("indexGeneration", generation);
        metadata.put("skuId", source.getSkuId());
        putIfPresent(metadata, "productId", source.getProductId());
        putIfPresent(metadata, "categoryId", source.getCategoryId());
        putIfPresent(metadata, "brandId", source.getBrandId());
        putIfPresent(metadata, "contentHash", source.getContentHash());
        if (source.getUpdatedAt() != null) {
            metadata.put("updatedAt", source.getUpdatedAt().getTime());
        }

        return Document.builder()
                .id(documentId(source.getSkuId()))
                .text(buildDocumentText(source))
                .metadata(metadata)
                .build();
    }

    public String documentId(Long skuId) {
        return "product-sku-" + skuId;
    }

    private String buildDocumentText(ProductKnowledgeDocumentVo source) {
        List<String> lines = new ArrayList<>();
        append(lines, "商品", source.getProductName());
        append(lines, "SKU", source.getSkuName());
        append(lines, "品牌", source.getBrandName());
        append(lines, "分类", source.getCategoryPath());
        append(lines, "商品规格", source.getProductSpec());
        append(lines, "SKU规格", source.getSkuSpec());
        append(lines, "计量单位", source.getUnitName());
        return String.join("\n", lines);
    }

    private void append(List<String> lines, String label, String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return;
        }
        String normalized = rawValue.trim()
                .replace('\r', ' ')
                .replace('\n', ' ');
        String value = normalized.length() <= MAX_TEXT_FIELD_LENGTH
                ? normalized
                : normalized.substring(0, MAX_TEXT_FIELD_LENGTH);
        lines.add(label + "：" + value);
    }

    private void putIfPresent(Map<String, Object> metadata,
                              String key,
                              Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }
}
