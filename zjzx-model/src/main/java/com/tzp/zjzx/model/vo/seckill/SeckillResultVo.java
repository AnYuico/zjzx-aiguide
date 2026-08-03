package com.tzp.zjzx.model.vo.seckill;

import lombok.Data;

@Data
public class SeckillResultVo {

    private String requestId;
    private String orderNo;
    private Integer status;
    private Long orderId;
    private String message;
}

