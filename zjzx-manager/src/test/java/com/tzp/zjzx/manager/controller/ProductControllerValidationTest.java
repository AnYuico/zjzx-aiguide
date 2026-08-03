package com.tzp.zjzx.manager.controller;

import com.tzp.zjzx.common.exception.GlobalExceptionHandler;
import com.tzp.zjzx.manager.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ProductControllerValidationTest {

    @Mock
    private ProductService productService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ProductController controller = new ProductController();
        ReflectionTestUtils.setField(controller, "productService", productService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void rejectsWeightContainingUnitBeforeServiceCall() throws Exception {
        String requestBody = "{\"name\":\"Mac mini\",\"productSkuList\":[{"
                + "\"weight\":\"0.5kg\",\"volume\":0.02}]}";

        mockMvc.perform(post("/admin/product/product/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(230))
                .andExpect(jsonPath("$.message")
                        .value("请求参数格式错误，请检查日期、数值及字段类型"));

        verifyNoInteractions(productService);
    }
}
