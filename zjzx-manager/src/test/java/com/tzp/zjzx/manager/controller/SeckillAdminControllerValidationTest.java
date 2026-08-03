package com.tzp.zjzx.manager.controller;

import com.tzp.zjzx.common.exception.GlobalExceptionHandler;
import com.tzp.zjzx.manager.client.ProductSeckillAdminClient;
import com.tzp.zjzx.model.dto.seckill.SeckillActivityCreateDto;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.text.SimpleDateFormat;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SeckillAdminControllerValidationTest {

    @Mock
    private ProductSeckillAdminClient seckillClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SeckillAdminController(seckillClient))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void acceptsDocumentedShanghaiDateTimeFormat() throws Exception {
        when(seckillClient.create(any(SeckillActivityCreateDto.class)))
                .thenReturn(Result.build(1L, ResultCodeEnum.SUCCESS));
        String requestBody = """
                {
                  "name": "Mac Mini flash sale",
                  "startTime": "2026-07-24 20:30:00",
                  "endTime": "2026-07-25 00:00:00",
                  "skuList": [
                    {"skuId": 14, "seckillPrice": 1999, "totalStock": 5}
                  ]
                }
                """;

        mockMvc.perform(post("/admin/product/seckill/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        ArgumentCaptor<SeckillActivityCreateDto> captor =
                ArgumentCaptor.forClass(SeckillActivityCreateDto.class);
        verify(seckillClient).create(captor.capture());
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        assertEquals("2026-07-24 20:30:00",
                formatter.format(captor.getValue().getStartTime()));
    }

    @Test
    void rejectsUnsupportedDateTimeFormatWithGenericMessage() throws Exception {
        String requestBody = """
                {
                  "name": "Mac Mini flash sale",
                  "startTime": "2026/07/24 20:30:00",
                  "endTime": "2026-07-25 00:00:00",
                  "skuList": [
                    {"skuId": 14, "seckillPrice": 1999, "totalStock": 5}
                  ]
                }
                """;

        mockMvc.perform(post("/admin/product/seckill/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(230))
                .andExpect(jsonPath("$.message")
                        .value("请求参数格式错误，请检查日期、数值及字段类型"));

        verifyNoInteractions(seckillClient);
    }
}
