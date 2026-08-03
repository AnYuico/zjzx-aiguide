package com.tzp.zjzx.model.entity.seckill;

import com.tzp.zjzx.model.entity.base.BaseEntity;
import lombok.Data;

import java.util.Date;

@Data
public class SeckillActivity extends BaseEntity {

    private String name;
    private Date startTime;
    private Date endTime;
    private Integer status;
}

