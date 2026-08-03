package com.tzp.zjzx.model.vo.seckill;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class SeckillActivityVo {

    private Long id;
    private String name;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date startTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date endTime;

    private Integer status;
    private List<SeckillSkuVo> skuList;
}
