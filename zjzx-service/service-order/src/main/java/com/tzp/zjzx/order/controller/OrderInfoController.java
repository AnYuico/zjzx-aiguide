package com.tzp.zjzx.order.controller;

import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.common.security.InternalApiAuth;
import com.tzp.zjzx.model.dto.h5.OrderInfoDto;
import com.tzp.zjzx.model.dto.internal.OrderPaymentInternalDto;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.h5.TradeVo;
import com.tzp.zjzx.model.vo.order.OrderDetailVo;
import com.tzp.zjzx.order.service.OrderInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Order API")
@RestController
@RequestMapping(value = "/api/order/orderInfo")
public class OrderInfoController {

    private final OrderInfoService orderInfoService;

    @Value("${zjzx.internal-api.token}")
    private String internalApiToken;

    public OrderInfoController(OrderInfoService orderInfoService) {
        this.orderInfoService = orderInfoService;
    }

    @Operation(summary = "Submit order")
    @PostMapping("auth/submitOrder")
    public Result<Long> submitOrder(@RequestBody OrderInfoDto orderInfoDto) {
        return Result.build(orderInfoService.submitOrder(orderInfoDto), ResultCodeEnum.SUCCESS);
    }

    @Operation(summary = "Checkout preview")
    @GetMapping("auth/trade")
    public Result<TradeVo> trade() {
        return Result.build(orderInfoService.getTrade(), ResultCodeEnum.SUCCESS);
    }

    @Operation(summary = "Get order detail")
    @GetMapping("auth/{orderId}")
    public Result<OrderDetailVo> getOrderInfo(
            @Parameter(name = "orderId", description = "Order ID", required = true)
            @PathVariable Long orderId) {
        return Result.build(orderInfoService.getOrderInfo(orderId), ResultCodeEnum.SUCCESS);
    }

    @Operation(summary = "Buy now")
    @GetMapping("auth/buy/{skuId}")
    public Result<TradeVo> buy(
            @Parameter(name = "skuId", description = "SKU ID", required = true)
            @PathVariable Long skuId) {
        return Result.build(orderInfoService.buy(skuId), ResultCodeEnum.SUCCESS);
    }

    @Operation(summary = "List current user's orders")
    @GetMapping("auth/{page}/{limit}")
    public Result<PageInfo<OrderDetailVo>> list(
            @PathVariable Integer page,
            @PathVariable Integer limit,
            @RequestParam(required = false) Integer orderStatus) {
        return Result.build(
                orderInfoService.findUserPage(page, limit, orderStatus),
                ResultCodeEnum.SUCCESS
        );
    }

    @Operation(summary = "Get current user's order by order number")
    @GetMapping("auth/getOrderInfoByOrderNo/{orderNo}")
    public Result<OrderDetailVo> getOrderInfoByOrderNo(@PathVariable String orderNo) {
        return Result.build(orderInfoService.getByOrderNo(orderNo), ResultCodeEnum.SUCCESS);
    }

    @Operation(summary = "Cancel current user's unpaid order")
    @PostMapping("auth/{orderNo}/cancel")
    public Result<Void> cancelOrder(@PathVariable String orderNo) {
        orderInfoService.cancelOrder(orderNo);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    @Operation(summary = "Hide current user's cancelled or completed order")
    @DeleteMapping("auth/{orderNo}")
    public Result<Void> deleteOrder(@PathVariable String orderNo) {
        orderInfoService.deleteOrder(orderNo);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }

    @GetMapping("internal/getByOrderNo/{orderNo}")
    public Result<OrderPaymentInternalDto> getOrderInfoByOrderNoInternal(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String token,
            @PathVariable String orderNo) {
        InternalApiAuth.verify(internalApiToken, token);
        return Result.build(orderInfoService.getByOrderNoInternal(orderNo), ResultCodeEnum.SUCCESS);
    }

    @PostMapping("internal/markPaid/{orderNo}/{orderStatus}")
    public Result<Void> updateOrderStatus(
            @RequestHeader(InternalApiAuth.HEADER_NAME) String token,
            @PathVariable String orderNo,
            @PathVariable Integer orderStatus) {
        InternalApiAuth.verify(internalApiToken, token);
        orderInfoService.updateOrderStatus(orderNo, orderStatus);
        return Result.build(null, ResultCodeEnum.SUCCESS);
    }
}
