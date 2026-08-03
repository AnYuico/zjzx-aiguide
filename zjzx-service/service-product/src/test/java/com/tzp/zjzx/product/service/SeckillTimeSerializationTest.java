package com.tzp.zjzx.product.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tzp.zjzx.model.vo.seckill.SeckillActivityAdminVo;
import com.tzp.zjzx.model.vo.seckill.SeckillActivityVo;
import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SeckillTimeSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesPublicAndAdminTimesInShanghaiTimeZone() throws Exception {
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        formatter.setTimeZone(TimeZone.getTimeZone("Asia/Shanghai"));
        Date startTime = formatter.parse("2026-07-24 15:30:05");

        SeckillActivityVo publicVo = new SeckillActivityVo();
        publicVo.setStartTime(startTime);
        SeckillActivityAdminVo adminVo = new SeckillActivityAdminVo();
        adminVo.setStartTime(startTime);

        assertTrue(objectMapper.writeValueAsString(publicVo)
                .contains("\"startTime\":\"2026-07-24 15:30:05\""));
        assertTrue(objectMapper.writeValueAsString(adminVo)
                .contains("\"startTime\":\"2026-07-24 15:30:05\""));
    }
}
