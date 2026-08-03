package com.tzp.zjzx.model.dto.h5;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class OrderInfoDto {

    //客户端一次提交订单的幂等标识
    private String requestId;

    //订单来源：1-购物车结算，2-立即购买
    private Integer orderSource;

    //送货地址id
    private Long userAddressId;

    //运费
    private BigDecimal feightFee;

    //备注
    private String remark;

    //订单明细
    private List<OrderSubmitItemDto> orderItemList;
}
