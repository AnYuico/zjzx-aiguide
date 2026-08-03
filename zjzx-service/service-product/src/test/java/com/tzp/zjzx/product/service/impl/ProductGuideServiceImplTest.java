package com.tzp.zjzx.product.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.ai.contract.dto.ProductKnowledgePageQueryDto;
import com.tzp.zjzx.ai.contract.dto.ProductGuideQueryDto;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgeDocumentVo;
import com.tzp.zjzx.ai.contract.vo.ProductKnowledgePageVo;
import com.tzp.zjzx.ai.contract.vo.ProductGuideVo;
import com.tzp.zjzx.product.mapper.ProductGuideMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductGuideServiceImplTest {

    @Mock
    private ProductGuideMapper productGuideMapper;

    @Test
    void searchNormalizesKeywordAndUsesBoundedLimit() {
        ProductGuideServiceImpl service = new ProductGuideServiceImpl(productGuideMapper);
        ProductGuideQueryDto query = new ProductGuideQueryDto();
        query.setKeyword("  milk  ");
        query.setLimit(5);
        List<ProductGuideVo> expected = List.of(new ProductGuideVo());
        when(productGuideMapper.search("milk", 5)).thenReturn(expected);

        List<ProductGuideVo> actual = service.search(query);

        assertSame(expected, actual);
        verify(productGuideMapper).search("milk", 5);
    }

    @Test
    void searchUsesSafeDefaultsWithoutModelArguments() {
        ProductGuideServiceImpl service = new ProductGuideServiceImpl(productGuideMapper);
        when(productGuideMapper.search(null, ProductGuideServiceImpl.DEFAULT_LIMIT))
                .thenReturn(List.of());

        List<ProductGuideVo> actual = service.search(null);

        assertEquals(0, actual.size());
        verify(productGuideMapper).search(null, ProductGuideServiceImpl.DEFAULT_LIMIT);
    }

    @Test
    void searchRejectsUnboundedOrOversizedInput() {
        ProductGuideServiceImpl service = new ProductGuideServiceImpl(productGuideMapper);
        ProductGuideQueryDto invalidLimit = new ProductGuideQueryDto();
        invalidLimit.setLimit(ProductGuideServiceImpl.MAX_LIMIT + 1);
        ProductGuideQueryDto invalidKeyword = new ProductGuideQueryDto();
        invalidKeyword.setKeyword("x".repeat(ProductGuideServiceImpl.MAX_KEYWORD_LENGTH + 1));

        assertThrows(MyException.class, () -> service.search(invalidLimit));
        assertThrows(MyException.class, () -> service.search(invalidKeyword));
    }

    @Test
    void getBySkuIdReturnsOnlyGuideProjection() {
        ProductGuideServiceImpl service = new ProductGuideServiceImpl(productGuideMapper);
        ProductGuideVo expected = new ProductGuideVo();
        expected.setSkuId(12L);
        when(productGuideMapper.getBySkuId(12L)).thenReturn(expected);

        ProductGuideVo actual = service.getBySkuId(12L);

        assertSame(expected, actual);
        verify(productGuideMapper).getBySkuId(12L);
        assertThrows(MyException.class, () -> service.getBySkuId(null));
    }

    @Test
    void knowledgePageUsesCursorAndGeneratesStableContentHash() {
        ProductGuideServiceImpl service = new ProductGuideServiceImpl(productGuideMapper);
        ProductKnowledgePageQueryDto query = new ProductKnowledgePageQueryDto();
        query.setAfterSkuId(10L);
        query.setLimit(2);
        ProductKnowledgeDocumentVo first = knowledgeDocument(11L, "Mac Mini");
        ProductKnowledgeDocumentVo second = knowledgeDocument(12L, "MacBook Air");
        ProductKnowledgeDocumentVo lookahead = knowledgeDocument(13L, "MacBook Pro");
        when(productGuideMapper.findKnowledgePage(10L, 3))
                .thenReturn(List.of(first, second, lookahead));

        ProductKnowledgePageVo page = service.getKnowledgePage(query);

        assertEquals(2, page.getItems().size());
        assertEquals(12L, page.getNextCursor());
        assertTrue(page.isHasMore());
        assertNotNull(page.getItems().get(0).getContentHash());
        assertEquals(64, page.getItems().get(0).getContentHash().length());
        verify(productGuideMapper).findKnowledgePage(10L, 3);
    }

    @Test
    void knowledgePageRejectsInvalidCursorOrLimit() {
        ProductGuideServiceImpl service = new ProductGuideServiceImpl(productGuideMapper);
        ProductKnowledgePageQueryDto invalidCursor = new ProductKnowledgePageQueryDto();
        invalidCursor.setAfterSkuId(-1L);
        ProductKnowledgePageQueryDto invalidLimit = new ProductKnowledgePageQueryDto();
        invalidLimit.setLimit(ProductGuideServiceImpl.MAX_KNOWLEDGE_PAGE_SIZE + 1);

        assertThrows(MyException.class, () -> service.getKnowledgePage(invalidCursor));
        assertThrows(MyException.class, () -> service.getKnowledgePage(invalidLimit));
    }

    @Test
    void knowledgePageReturnsCurrentCursorWhenNoRowsExist() {
        ProductGuideServiceImpl service = new ProductGuideServiceImpl(productGuideMapper);
        ProductKnowledgePageQueryDto query = new ProductKnowledgePageQueryDto();
        query.setAfterSkuId(20L);
        when(productGuideMapper.findKnowledgePage(
                20L,
                ProductGuideServiceImpl.DEFAULT_KNOWLEDGE_PAGE_SIZE + 1
        )).thenReturn(List.of());

        ProductKnowledgePageVo page = service.getKnowledgePage(query);

        assertEquals(20L, page.getNextCursor());
        assertFalse(page.isHasMore());
        assertTrue(page.getItems().isEmpty());
    }

    @Test
    void productKnowledgeSnapshotHashesEveryCurrentSku() {
        ProductGuideServiceImpl service =
                new ProductGuideServiceImpl(productGuideMapper);
        ProductKnowledgeDocumentVo first =
                knowledgeDocument(11L, "Mac Mini");
        ProductKnowledgeDocumentVo second =
                knowledgeDocument(12L, "Mac Mini");
        when(productGuideMapper.findKnowledgeByProductId(1L))
                .thenReturn(List.of(first, second));

        List<ProductKnowledgeDocumentVo> documents =
                service.getKnowledgeByProductId(1L);

        assertEquals(2, documents.size());
        assertEquals(64, documents.get(0).getContentHash().length());
        assertEquals(64, documents.get(1).getContentHash().length());
        verify(productGuideMapper).findKnowledgeByProductId(1L);
        assertThrows(
                MyException.class,
                () -> service.getKnowledgeByProductId(0L)
        );
    }

    private ProductKnowledgeDocumentVo knowledgeDocument(Long skuId, String productName) {
        ProductKnowledgeDocumentVo document = new ProductKnowledgeDocumentVo();
        document.setProductId(1L);
        document.setSkuId(skuId);
        document.setBrandId(2L);
        document.setCategoryId(3L);
        document.setProductName(productName);
        document.setSkuName(productName + " 16G");
        document.setBrandName("Apple");
        document.setCategoryPath("Digital > Computer");
        document.setProductSpec("{\"memory\":\"16G\"}");
        document.setSkuSpec("{\"color\":\"silver\"}");
        document.setUnitName("unit");
        return document;
    }
}
