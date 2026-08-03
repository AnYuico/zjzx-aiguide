package com.tzp.zjzx.model.dto.seckill;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.Date;
import java.util.List;

@Data
public class SeckillActivityCreateDto {

    @NotBlank(message = "活动名称不能为空")
    @Size(max = 100, message = "活动名称不能超过100个字符")
    private String name;

    @NotNull(message = "活动开始时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date startTime;

    @NotNull(message = "活动结束时间不能为空")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "Asia/Shanghai")
    private Date endTime;

    @Valid
    @NotEmpty(message = "活动至少需要一个SKU")
    @Size(max = 100, message = "单个活动最多包含100个SKU")
    private List<SeckillSkuCreateDto> skuList;
}
