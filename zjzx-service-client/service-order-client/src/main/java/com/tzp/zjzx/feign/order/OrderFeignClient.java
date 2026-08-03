package com.tzp.zjzx.feign.order;

import com.tzp.zjzx.model.dto.internal.OrderPaymentInternalDto;
import com.tzp.zjzx.model.vo.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(value = "service-order")
public interface OrderFeignClient {

    @GetMapping("/api/order/orderInfo/internal/getByOrderNo/{orderNo}")
    Result<OrderPaymentInternalDto> getOrderInfoByOrderNo(
            @RequestHeader("X-Internal-Token") String internalToken,
            @PathVariable String orderNo);


    @PostMapping("/api/order/orderInfo/internal/markPaid/{orderNo}/{orderStatus}")
    Result<Void> updateOrderStatus(@RequestHeader("X-Internal-Token") String internalToken,
                                   @PathVariable(value = "orderNo") String orderNo,
                                   @PathVariable(value = "orderStatus") Integer orderStatus);

}
