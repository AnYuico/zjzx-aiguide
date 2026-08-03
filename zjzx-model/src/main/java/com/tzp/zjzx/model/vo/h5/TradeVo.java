package com.tzp.zjzx.model.vo.h5;

import com.tzp.zjzx.model.vo.order.OrderItemVo;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Schema(description = "结算实体类")
public class TradeVo {

    @Schema(description = "订单来源【1->购物车结算；2->立即购买】")
    private Integer orderSource;

    @Schema(description = "结算总金额")
    private BigDecimal totalAmount;

    @Schema(description = "结算商品列表")
    private List<OrderItemVo> orderItemList;

}
