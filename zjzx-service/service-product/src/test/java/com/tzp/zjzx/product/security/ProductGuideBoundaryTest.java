package com.tzp.zjzx.product.security;

import com.tzp.zjzx.ai.contract.dto.ProductKnowledgePageQueryDto;
import com.tzp.zjzx.ai.contract.dto.ProductGuideQueryDto;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgeDocumentVo;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductGuideBoundaryTest {

    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "skuId",
            "productName",
            "skuName",
            "thumbImg",
            "salePrice",
            "marketPrice",
            "skuSpec",
            "unitName",
            "inStock"
    );
    private static final Set<String> ALLOWED_KNOWLEDGE_FIELDS = Set.of(
            "productId",
            "skuId",
            "categoryId",
            "brandId",
            "productName",
            "skuName",
            "brandName",
            "categoryPath",
            "productSpec",
            "skuSpec",
            "unitName",
            "contentHash",
            "updatedAt"
    );

    @Test
    void guideVoContainsOnlyAllowlistedFields() {
        Set<String> actualFields = Arrays.stream(ProductGuideVo.class.getDeclaredFields())
                .map(field -> field.getName())
                .collect(Collectors.toSet());

        assertEquals(ALLOWED_FIELDS, actualFields);
        assertNoGetter(ProductGuideVo.class, "getCostPrice");
        assertNoGetter(ProductGuideVo.class, "getStockNum");
        assertNoGetter(ProductGuideVo.class, "getSaleNum");
        assertNoGetter(ProductGuideVo.class, "getUserId");
        assertNoGetter(ProductGuideVo.class, "getOrderNo");
    }

    @Test
    void modelQueryCannotSupplyUserOrOrderIdentity() {
        assertNoGetter(ProductGuideQueryDto.class, "getUserId");
        assertNoGetter(ProductGuideQueryDto.class, "getOrderNo");
        assertNoGetter(ProductKnowledgePageQueryDto.class, "getUserId");
        assertNoGetter(ProductKnowledgePageQueryDto.class, "getOrderNo");
    }

    @Test
    void knowledgeDocumentContainsOnlyIndexAllowlistedFields() {
        Set<String> actualFields = Arrays.stream(ProductKnowledgeDocumentVo.class.getDeclaredFields())
                .map(field -> field.getName())
                .collect(Collectors.toSet());

        assertEquals(ALLOWED_KNOWLEDGE_FIELDS, actualFields);
        assertNoGetter(ProductKnowledgeDocumentVo.class, "getCostPrice");
        assertNoGetter(ProductKnowledgeDocumentVo.class, "getStockNum");
        assertNoGetter(ProductKnowledgeDocumentVo.class, "getUserId");
        assertNoGetter(ProductKnowledgeDocumentVo.class, "getOrderNo");
    }

    @Test
    void guideSqlDoesNotSelectSensitiveBusinessColumnsOrTables() throws IOException {
        try (InputStream inputStream = getClass().getResourceAsStream(
                "/mapper/product/ProductGuideMapper.xml")) {
            assertNotNull(inputStream);
            String mapperXml = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
                    .toLowerCase();

            assertFalse(mapperXml.contains("cost_price"));
            assertFalse(mapperXml.contains("order_info"));
            assertFalse(mapperXml.contains("user_address"));
        }
    }

    private void assertNoGetter(Class<?> type, String getterName) {
        assertThrows(NoSuchMethodException.class, () -> type.getMethod(getterName));
    }
}
