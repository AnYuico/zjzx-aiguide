package com.tzp.zjzx.model.vo.seckill;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SeckillSubmitVo {

    private String requestId;
    private String orderNo;
    private Integer status;
    private String message;
}

