package com.tzp.zjzx.order.service.impl;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.tzp.zjzx.ai.contract.dto.AgentOrderCancelRequestDto;
import com.tzp.zjzx.ai.contract.dto.AgentOrderCancellationCandidateDto;
import com.tzp.zjzx.ai.contract.vo.AgentOrderCancellationResultVo;
import com.tzp.zjzx.ai.contract.vo.AgentOrderSummaryVo;
import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.feign.CartFeignClient;
import com.tzp.zjzx.feign.product.ProductFeignClient;
import com.tzp.zjzx.feign.user.UserFeignClient;
import com.tzp.zjzx.model.dto.h5.OrderInfoDto;
import com.tzp.zjzx.model.dto.h5.OrderSubmitItemDto;
import com.tzp.zjzx.model.dto.internal.CartItemInternalDto;
import com.tzp.zjzx.model.dto.internal.OrderPaymentInternalDto;
import com.tzp.zjzx.model.dto.internal.ProductSkuInternalDto;
import com.tzp.zjzx.model.dto.internal.UserAddressInternalDto;
import com.tzp.zjzx.model.dto.internal.UserProfileInternalDto;
import com.tzp.zjzx.model.dto.product.StockItemDto;
import com.tzp.zjzx.model.dto.product.StockReserveRequest;
import com.tzp.zjzx.model.entity.order.OrderInfo;
import com.tzp.zjzx.model.entity.order.OrderItem;
import com.tzp.zjzx.model.entity.order.OrderLog;
import com.tzp.zjzx.model.entity.order.OrderSubmitRequest;
import com.tzp.zjzx.model.entity.user.UserInfo;
import com.tzp.zjzx.model.enums.InventoryOperationType;
import com.tzp.zjzx.model.enums.OrderSource;
import com.tzp.zjzx.model.enums.OrderStatus;
import com.tzp.zjzx.model.vo.common.Result;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.model.vo.h5.TradeVo;
import com.tzp.zjzx.model.vo.order.OrderDetailVo;
import com.tzp.zjzx.model.vo.order.OrderItemVo;
import com.tzp.zjzx.model.enums.OrderSubmitRequestStatus;
import com.tzp.zjzx.order.mapper.OrderInfoMapper;
import com.tzp.zjzx.order.mapper.OrderItemMapper;
import com.tzp.zjzx.order.mapper.OrderLogMapper;
import com.tzp.zjzx.order.exception.AgentOrderActionException;
import com.tzp.zjzx.order.service.OrderInfoService;
import com.tzp.zjzx.order.service.OrderCreationService;
import com.tzp.zjzx.order.service.OrderMqEventService;
import com.tzp.zjzx.order.service.OrderSubmitRequestService;
import com.tzp.zjzx.order.service.StockReleaseCompensationService;
import com.tzp.zjzx.utils.AuthContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.beans.BeanUtils;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class OrderInfoServiceImpl implements OrderInfoService {

    private static final String USER_CANCEL_REASON = "Cancelled by user";
    private static final String AGENT_CANCEL_REASON =
            "Cancelled by shopping guide after user confirmation";
    private static final int MAX_AGENT_ORDER_LIMIT = 10;
    private static final DateTimeFormatter AGENT_TIME_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.of("Asia/Shanghai"));


    @Autowired
    private CartFeignClient cartFeignClient;

    @Autowired
    private ProductFeignClient productFeignClient;

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OrderItemMapper orderItemMapper;

    @Autowired
    private OrderLogMapper orderLogMapper;

    @Autowired
    private UserFeignClient userFeignClient;

    @Autowired
    private OrderSubmitRequestService orderSubmitRequestService;

    @Autowired
    private OrderCreationService orderCreationService;

    @Autowired
    private StockReleaseCompensationService stockReleaseCompensationService;

    @Autowired
    private OrderMqEventService orderMqEventService;

    @Value("${zjzx.order.payment-timeout-minutes:30}")
    private long paymentTimeoutMinutes;

    @Value("${zjzx.internal-api.token}")
    private String internalApiToken;

    /**
     * 获取订单信息
     * @return
     */
    @Override
    public TradeVo getTrade() {

        //1 获取当前登录的用户的id
        //Long userId = AuthContextUtil.getUserInfo().getId();


        //2 远程调用 获取购物车中选中的商品列表
        List<CartItemInternalDto> cartInfoList = cartFeignClient.getAllChecked(
                internalApiToken, getCurrentUserId());
        //3 创建集合用于封装订单项
        List<OrderItemVo> orderItemList = new ArrayList<>();
        //3.1 将购物项数据转换成功订单明细数据
        for (CartItemInternalDto cartInfo : cartInfoList) {
            OrderItemVo orderItem = new OrderItemVo();
            orderItem.setSkuId(cartInfo.getSkuId());
            orderItem.setSkuName(cartInfo.getSkuName());
            orderItem.setSkuNum(cartInfo.getSkuNum());
            orderItem.setSkuPrice(cartInfo.getCartPrice());
            orderItem.setThumbImg(cartInfo.getImgUrl());
            orderItemList.add(orderItem);
        }

        // 计算总金额
        BigDecimal totalAmount = new BigDecimal(0);
        for(OrderItemVo orderItem : orderItemList) {
            totalAmount = totalAmount.add(orderItem.getSkuPrice().multiply(new BigDecimal(orderItem.getSkuNum())));
        }

        //4 封装订单信息
        TradeVo tradeVo = new TradeVo();
        tradeVo.setOrderSource(OrderSource.CART.getCode());
        tradeVo.setTotalAmount(totalAmount);
        tradeVo.setOrderItemList(orderItemList);
        return tradeVo;

    }

    /**
     * 提交订单
     * @param orderInfoDto
     * @return
     */
    @Override
    public Long submitOrder(OrderInfoDto orderInfoDto) {
        UserInfo userInfo = AuthContextUtil.getUserInfo();
        if (orderInfoDto == null || userInfo == null
                || !StringUtils.hasText(orderInfoDto.getRequestId())
                || CollectionUtils.isEmpty(orderInfoDto.getOrderItemList())) {
            throw new MyException(ResultCodeEnum.ORDER_SUBMIT_REQUEST_INVALID);
        }
        OrderSource orderSource = OrderSource.fromCode(orderInfoDto.getOrderSource());
        if (orderSource == null) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }

        OrderSubmitRequest submitRequest = orderSubmitRequestService.claim(
                orderInfoDto.getRequestId(), userInfo.getId());
        if (Objects.equals(submitRequest.getStatus(), OrderSubmitRequestStatus.SUCCESS.getCode())) {
            return submitRequest.getOrderId();
        }

        List<OrderItem> orderItemList = buildAuthoritativeOrderItems(orderInfoDto.getOrderItemList());
        if (CollectionUtils.isEmpty(orderItemList)) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }

        OrderInfo orderInfo = new OrderInfo();
        orderInfo.setOrderNo(submitRequest.getOrderNo());
        orderInfo.setRequestId(orderInfoDto.getRequestId());
        orderInfo.setOrderSource(orderSource.getCode());
        orderInfo.setUserId(userInfo.getId());
        UserProfileInternalDto currentUser = userFeignClient.getUserInfo(internalApiToken, userInfo.getId());
        if (currentUser == null) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        orderInfo.setNickName(currentUser.getNickName());
        UserAddressInternalDto userAddress = userFeignClient.getUserAddress(
                internalApiToken, userInfo.getId(), orderInfoDto.getUserAddressId());
        if (userAddress == null) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        orderInfo.setReceiverName(userAddress.getName());
        orderInfo.setReceiverPhone(userAddress.getPhone());
        orderInfo.setReceiverTagName(userAddress.getTagName());
        orderInfo.setReceiverProvince(userAddress.getProvinceCode());
        orderInfo.setReceiverCity(userAddress.getCityCode());
        orderInfo.setReceiverDistrict(userAddress.getDistrictCode());
        orderInfo.setReceiverAddress(userAddress.getFullAddress());
        BigDecimal totalAmount = BigDecimal.ZERO;
        for (OrderItem orderItem : orderItemList) {
            totalAmount = totalAmount.add(orderItem.getSkuPrice().multiply(new BigDecimal(orderItem.getSkuNum())));
        }
        orderInfo.setTotalAmount(totalAmount);
        orderInfo.setCouponAmount(new BigDecimal(0));
        orderInfo.setOriginalTotalAmount(totalAmount);
        orderInfo.setFeightFee(orderInfoDto.getFeightFee() == null
                ? BigDecimal.ZERO : orderInfoDto.getFeightFee());
        orderInfo.setPayType(2);
        orderInfo.setOrderStatus(0);
        orderInfo.setRemark(orderInfoDto.getRemark());
        orderInfo.setExpireTime(new Date(System.currentTimeMillis()
                + paymentTimeoutMinutes * 60_000L));

        StockReserveRequest reserveRequest = new StockReserveRequest();
        reserveRequest.setOrderNo(orderInfo.getOrderNo());
        List<StockItemDto> stockItems = new ArrayList<>();
        orderItemList.forEach(item -> stockItems.add(
                new StockItemDto(item.getSkuId(), item.getSkuNum())));
        reserveRequest.setItems(stockItems);

        requireInventorySuccess(productFeignClient.reserveStock(internalApiToken, reserveRequest));

        Long orderId;
        try {
            orderId = orderCreationService.createOrder(orderInfo, orderItemList);
        } catch (DuplicateKeyException ex) {
            OrderInfo existingOrder = orderInfoMapper.getByRequestId(orderInfoDto.getRequestId());
            if (existingOrder != null) {
                return existingOrder.getId();
            }
            releaseAfterCreateFailure(orderInfo, userInfo, ex);
            throw ex;
        } catch (RuntimeException ex) {
            releaseAfterCreateFailure(orderInfo, userInfo, ex);
            throw ex;
        }

        return orderId;
    }

    private List<OrderItem> buildAuthoritativeOrderItems(List<OrderSubmitItemDto> submittedItems) {
        Map<Long, Integer> quantities = new TreeMap<>();
        try {
            for (OrderSubmitItemDto submitted : submittedItems) {
                if (submitted == null || submitted.getSkuId() == null
                        || submitted.getSkuNum() == null || submitted.getSkuNum() <= 0) {
                    throw new MyException(ResultCodeEnum.DATA_ERROR);
                }
                quantities.merge(submitted.getSkuId(), submitted.getSkuNum(), Math::addExact);
            }
        } catch (ArithmeticException ex) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }

        List<OrderItem> orderItems = new ArrayList<>();
        quantities.forEach((skuId, skuNum) -> {
            ProductSkuInternalDto productSku = getProductSkuInternal(skuId);
            if (productSku == null || !Objects.equals(productSku.getStatus(), 1)
                    || Objects.equals(productSku.getIsDeleted(), 1)) {
                throw new MyException(ResultCodeEnum.DATA_ERROR);
            }
            OrderItem item = new OrderItem();
            item.setSkuId(skuId);
            item.setSkuNum(skuNum);
            item.setSkuName(productSku.getSkuName());
            item.setSkuPrice(productSku.getSalePrice());
            item.setThumbImg(productSku.getThumbImg());
            orderItems.add(item);
        });
        return orderItems;
    }

    private void releaseAfterCreateFailure(OrderInfo orderInfo, UserInfo userInfo, RuntimeException cause) {
        try {
            requireInventorySuccess(productFeignClient.releaseStock(
                    internalApiToken, orderInfo.getOrderNo()));
        } catch (RuntimeException releaseException) {
            cause.addSuppressed(releaseException);
            log.error("Failed to release reserved stock for order {}", orderInfo.getOrderNo(), releaseException);
            stockReleaseCompensationService.record(orderInfo.getOrderNo());
        } finally {
            orderSubmitRequestService.markFailed(orderInfo.getRequestId(), userInfo.getId());
        }
    }


    /**
     * 获取订单信息
     * @param orderId
     * @return
     */
    @Override
    public OrderDetailVo getOrderInfo(Long orderId) {
        OrderInfo orderInfo = orderInfoMapper.getByIdAndUserId(orderId, getCurrentUserId());
        return toOrderDetailVo(populateOrderItems(orderInfo));
    }

    /**
     * 立即购买
     * @param skuId
     * @return
     */
    @Override
    public TradeVo buy(Long skuId) {
        // 查询商品
        ProductSkuInternalDto productSku = getProductSkuInternal(skuId);
        if (productSku == null || !Objects.equals(productSku.getStatus(), 1)
                || Objects.equals(productSku.getIsDeleted(), 1)) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        List<OrderItemVo> orderItemList = new ArrayList<>();
        OrderItemVo orderItem = new OrderItemVo();
        orderItem.setSkuId(skuId);
        orderItem.setSkuName(productSku.getSkuName());
        orderItem.setSkuNum(1);
        orderItem.setSkuPrice(productSku.getSalePrice());
        orderItem.setThumbImg(productSku.getThumbImg());
        orderItemList.add(orderItem);

        // 计算总金额
        BigDecimal totalAmount = productSku.getSalePrice();
        TradeVo tradeVo = new TradeVo();
        tradeVo.setOrderSource(OrderSource.BUY_NOW.getCode());
        tradeVo.setTotalAmount(totalAmount);
        tradeVo.setOrderItemList(orderItemList);

        // 返回
        return tradeVo;
    }

    /**
     * 分页查询订单
     * @param page
     * @param limit
     * @param orderStatus
     * @return
     */
    @Override
    public PageInfo<OrderDetailVo> findUserPage(Integer page,
                                                Integer limit,
                                                Integer orderStatus) {
        PageHelper.startPage(page, limit);
        Long userId = getCurrentUserId();
        //查询订单信息
        List<OrderInfo> orderInfoList = orderInfoMapper.findUserPage(userId, orderStatus);

        //查询订单项信息 并将订单项信息封装进订单信息中
        orderInfoList.forEach(orderInfo -> {
            //根据订单id查询订单项
            List<OrderItem> orderItem = orderItemMapper.findByOrderId(orderInfo.getId());
            orderInfo.setOrderItemList(orderItem);
        });

        PageInfo<OrderInfo> entityPage = new PageInfo<>(orderInfoList);
        PageInfo<OrderDetailVo> result = new PageInfo<>();
        BeanUtils.copyProperties(entityPage, result, "list");
        result.setList(orderInfoList.stream()
                .map(this::toOrderDetailVo)
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 根据订单号查询订单
     * @param orderNo
     * @return
     */
    @Override
    public OrderDetailVo getByOrderNo(String orderNo) {
        OrderInfo orderInfo = orderInfoMapper.getByOrderNoAndUserId(orderNo, getCurrentUserId());
        return toOrderDetailVo(populateOrderItems(orderInfo));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(String orderNo) {
        if (!StringUtils.hasText(orderNo) || orderNo.length() > 64) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }

        Long userId = getCurrentUserId();
        Date cancelTime = new Date();
        int updated = orderInfoMapper.cancelPendingByUser(
                orderNo, userId, cancelTime, USER_CANCEL_REASON);
        OrderInfo orderInfo = orderInfoMapper.getByOrderNoAndUserId(orderNo, userId);
        if (orderInfo == null) {
            throw new MyException(ResultCodeEnum.ORDER_NOT_FOUND);
        }
        if (updated == 0) {
            if (Integer.valueOf(OrderStatus.CANCELLED.getCode())
                    .equals(orderInfo.getOrderStatus())) {
                return;
            }
            throw new MyException(ResultCodeEnum.ORDER_CANNOT_CANCEL);
        }

        OrderLog orderLog = new OrderLog();
        orderLog.setOrderId(orderInfo.getId());
        orderLog.setOperateUser("USER:" + userId);
        orderLog.setProcessStatus(OrderStatus.CANCELLED.getCode());
        orderLog.setNote(USER_CANCEL_REASON);
        orderLogMapper.save(orderLog);

        orderMqEventService.enqueueInventoryOperation(
                orderInfo, InventoryOperationType.RELEASE);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(String orderNo) {
        if (!StringUtils.hasText(orderNo) || orderNo.length() > 64) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }

        Long userId = getCurrentUserId();
        OrderInfo orderInfo = orderInfoMapper.getByOrderNoAndUserIdIncludingDeleted(
                orderNo, userId);
        if (orderInfo == null) {
            throw new MyException(ResultCodeEnum.ORDER_NOT_FOUND);
        }
        if (Integer.valueOf(1).equals(orderInfo.getUserDeleted())) {
            return;
        }
        if (!isUserDeletable(orderInfo.getOrderStatus())) {
            throw new MyException(ResultCodeEnum.ORDER_CANNOT_DELETE);
        }

        int updated = orderInfoMapper.hideByUser(
                orderNo, userId,
                OrderStatus.CANCELLED.getCode(), OrderStatus.COMPLETED.getCode());
        if (updated == 0) {
            OrderInfo current = orderInfoMapper.getByOrderNoAndUserIdIncludingDeleted(
                    orderNo, userId);
            if (current != null && Integer.valueOf(1).equals(current.getUserDeleted())) {
                return;
            }
            throw new MyException(ResultCodeEnum.ORDER_CANNOT_DELETE);
        }

        OrderLog orderLog = new OrderLog();
        orderLog.setOrderId(orderInfo.getId());
        orderLog.setOperateUser("USER:" + userId);
        orderLog.setProcessStatus(orderInfo.getOrderStatus());
        orderLog.setNote("Hidden by user");
        orderLogMapper.save(orderLog);
    }

    private boolean isUserDeletable(Integer orderStatus) {
        return Integer.valueOf(OrderStatus.CANCELLED.getCode()).equals(orderStatus)
                || Integer.valueOf(OrderStatus.COMPLETED.getCode()).equals(orderStatus);
    }

    @Override
    public OrderPaymentInternalDto getByOrderNoInternal(String orderNo) {
        if (!StringUtils.hasText(orderNo) || orderNo.length() > 64) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }
        OrderInfo orderInfo = orderInfoMapper.getByOrderNo(orderNo);
        if (orderInfo == null) {
            throw new MyException(ResultCodeEnum.ORDER_NOT_FOUND);
        }
        orderInfo = populateOrderItems(orderInfo);
        OrderPaymentInternalDto result = new OrderPaymentInternalDto();
        result.setUserId(orderInfo.getUserId());
        result.setOrderNo(orderInfo.getOrderNo());
        result.setTotalAmount(orderInfo.getTotalAmount());
        result.setPayType(orderInfo.getPayType());
        result.setOrderStatus(orderInfo.getOrderStatus());
        result.setSkuNames(orderInfo.getOrderItemList().stream()
                .map(OrderItem::getSkuName)
                .collect(Collectors.toList()));
        return result;
    }

    @Override
    public List<AgentOrderSummaryVo> findAgentRecentOrders(
            Long userId,
            String status,
            Integer limit) {
        if (userId == null || userId <= 0
                || limit == null
                || limit < 1
                || limit > MAX_AGENT_ORDER_LIMIT) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }
        Integer orderStatus = parseAgentOrderStatus(status);
        List<OrderInfo> orders = orderInfoMapper.findAgentRecentOrders(
                userId,
                orderStatus,
                limit
        );
        List<AgentOrderSummaryVo> result = new ArrayList<>();
        for (int index = 0; index < orders.size(); index++) {
            OrderInfo order = orders.get(index);
            AgentOrderSummaryVo summary = new AgentOrderSummaryVo();
            summary.setRecentPosition(index + 1);
            summary.setStatus(agentOrderStatusName(order.getOrderStatus()));
            summary.setStatusText(agentOrderStatusText(order.getOrderStatus()));
            summary.setTotalAmount(order.getTotalAmount());
            summary.setCreatedAt(formatAgentTime(order.getCreateTime()));
            summary.setExpiresAt(formatAgentTime(order.getExpireTime()));
            summary.setProductNames(findAgentProductNames(order.getId()));
            result.add(summary);
        }
        return result;
    }

    @Override
    public AgentOrderCancellationCandidateDto findAgentCancellationCandidate(
            Long userId,
            Integer recentPosition) {
        if (userId == null || userId <= 0
                || recentPosition == null
                || recentPosition < 1
                || recentPosition > MAX_AGENT_ORDER_LIMIT) {
            throw new AgentOrderActionException(
                    AgentOrderActionException.Reason.INVALID_REQUEST,
                    "Invalid cancellation candidate request"
            );
        }
        List<OrderInfo> waitingOrders = orderInfoMapper.findAgentRecentOrders(
                userId,
                OrderStatus.WAITING_PAYMENT.getCode(),
                recentPosition
        );
        if (waitingOrders == null
                || waitingOrders.size() < recentPosition) {
            throw new AgentOrderActionException(
                    AgentOrderActionException.Reason.NOT_FOUND,
                    "Waiting-payment order does not exist"
            );
        }
        OrderInfo order = waitingOrders.get(recentPosition - 1);
        AgentOrderCancellationCandidateDto result =
                new AgentOrderCancellationCandidateDto();
        result.setRecentPosition(recentPosition);
        result.setOrderNo(order.getOrderNo());
        result.setTotalAmount(order.getTotalAmount());
        result.setCreatedAt(formatAgentTime(order.getCreateTime()));
        result.setProductNames(findAgentProductNames(order.getId()));
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AgentOrderCancellationResultVo cancelAgentOrder(
            Long userId,
            AgentOrderCancelRequestDto request) {
        validateAgentCancelRequest(userId, request);
        Date cancelTime = new Date();
        int updated = orderInfoMapper.cancelPendingByUser(
                request.getOrderNo(),
                userId,
                cancelTime,
                AGENT_CANCEL_REASON
        );
        OrderInfo order = orderInfoMapper.getByOrderNoAndUserId(
                request.getOrderNo(),
                userId
        );
        if (order == null) {
            throw new AgentOrderActionException(
                    AgentOrderActionException.Reason.NOT_FOUND,
                    "Order does not exist"
            );
        }
        if (updated == 0) {
            if (Integer.valueOf(OrderStatus.CANCELLED.getCode())
                    .equals(order.getOrderStatus())) {
                return agentCancellationResult(false, true);
            }
            throw new AgentOrderActionException(
                    AgentOrderActionException.Reason.CONFLICT,
                    "Order is no longer waiting for payment"
            );
        }

        OrderLog orderLog = new OrderLog();
        orderLog.setOrderId(order.getId());
        orderLog.setOperateUser("AGENT:" + userId);
        orderLog.setProcessStatus(OrderStatus.CANCELLED.getCode());
        orderLog.setNote(AGENT_CANCEL_REASON);
        orderLogMapper.save(orderLog);
        orderMqEventService.enqueueInventoryOperation(
                order,
                InventoryOperationType.RELEASE
        );
        return agentCancellationResult(true, false);
    }

    private void validateAgentCancelRequest(
            Long userId,
            AgentOrderCancelRequestDto request) {
        if (userId == null || userId <= 0 || request == null
                || !StringUtils.hasText(request.getRequestId())
                || !StringUtils.hasText(request.getOrderNo())
                || request.getOrderNo().length() > 64) {
            throw new AgentOrderActionException(
                    AgentOrderActionException.Reason.INVALID_REQUEST,
                    "Invalid cancellation request"
            );
        }
        try {
            UUID.fromString(request.getRequestId());
        } catch (IllegalArgumentException exception) {
            throw new AgentOrderActionException(
                    AgentOrderActionException.Reason.INVALID_REQUEST,
                    "Request ID must be a UUID"
            );
        }
    }

    private AgentOrderCancellationResultVo agentCancellationResult(
            boolean applied,
            boolean replayed) {
        AgentOrderCancellationResultVo result =
                new AgentOrderCancellationResultVo();
        result.setApplied(applied);
        result.setReplayed(replayed);
        return result;
    }

    private List<String> findAgentProductNames(Long orderId) {
        return orderItemMapper.findByOrderId(orderId)
                .stream()
                .map(OrderItem::getSkuName)
                .filter(StringUtils::hasText)
                .distinct()
                .collect(Collectors.toList());
    }

    private Integer parseAgentOrderStatus(String status) {
        if (!StringUtils.hasText(status)
                || "ALL".equalsIgnoreCase(status.trim())) {
            return null;
        }
        try {
            return OrderStatus.valueOf(status.trim().toUpperCase())
                    .getCode();
        } catch (IllegalArgumentException exception) {
            throw new MyException(ResultCodeEnum.REQUEST_PARAM_INVALID);
        }
    }

    private String agentOrderStatusName(Integer status) {
        for (OrderStatus candidate : OrderStatus.values()) {
            if (Integer.valueOf(candidate.getCode()).equals(status)) {
                return candidate.name();
            }
        }
        return "UNKNOWN";
    }

    private String agentOrderStatusText(Integer status) {
        if (status == null) {
            return "Unknown";
        }
        return switch (status) {
            case -1 -> "Cancelled";
            case 0 -> "Waiting for payment";
            case 1 -> "Waiting for delivery";
            case 2 -> "Delivered";
            case 3 -> "Completed";
            default -> "Unknown";
        };
    }

    private String formatAgentTime(Date value) {
        return value == null ? null : AGENT_TIME_FORMAT.format(
                value.toInstant()
        );
    }

    private OrderInfo populateOrderItems(OrderInfo orderInfo) {
        if (orderInfo == null) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        List<OrderItem> orderItem = orderItemMapper.findByOrderId(orderInfo.getId());
        orderInfo.setOrderItemList(orderItem);
        return orderInfo;
    }

    private OrderDetailVo toOrderDetailVo(OrderInfo orderInfo) {
        OrderDetailVo result = new OrderDetailVo();
        BeanUtils.copyProperties(orderInfo, result, "orderItemList");
        result.setOrderItemList(orderInfo.getOrderItemList().stream()
                .map(this::toOrderItemVo)
                .collect(Collectors.toList()));
        return result;
    }

    private OrderItemVo toOrderItemVo(OrderItem orderItem) {
        OrderItemVo result = new OrderItemVo();
        BeanUtils.copyProperties(orderItem, result);
        return result;
    }

    private Long getCurrentUserId() {
        UserInfo userInfo = AuthContextUtil.getUserInfo();
        if (userInfo == null || userInfo.getId() == null) {
            throw new MyException(ResultCodeEnum.LOGIN_AUTH);
        }
        return userInfo.getId();
    }

    private ProductSkuInternalDto getProductSkuInternal(Long skuId) {
        Result<ProductSkuInternalDto> result =
                productFeignClient.getBySkuId(internalApiToken, skuId);
        if (isSuccessful(result) && result.getData() != null) {
            return result.getData();
        }
        log.warn("Product SKU internal API failed: skuId={}, code={}, message={}",
                skuId, result == null ? null : result.getCode(),
                result == null ? null : result.getMessage());
        throw new MyException(ResultCodeEnum.DATA_ERROR);
    }

    private void requireInventorySuccess(Result<Boolean> result) {
        if (isSuccessful(result) && Boolean.TRUE.equals(result.getData())) {
            return;
        }
        log.warn("Product inventory internal API failed: code={}, message={}",
                result == null ? null : result.getCode(),
                result == null ? null : result.getMessage());
        throw new MyException(resolveInventoryError(result));
    }

    private boolean isSuccessful(Result<?> result) {
        return result != null
                && ResultCodeEnum.SUCCESS.getCode().equals(result.getCode());
    }

    private ResultCodeEnum resolveInventoryError(Result<?> result) {
        if (result != null) {
            if (ResultCodeEnum.STOCK_LESS.getCode().equals(result.getCode())) {
                return ResultCodeEnum.STOCK_LESS;
            }
            if (ResultCodeEnum.STOCK_REQUEST_INVALID.getCode().equals(result.getCode())) {
                return ResultCodeEnum.STOCK_REQUEST_INVALID;
            }
            if (ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR.getCode().equals(result.getCode())) {
                return ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR;
            }
            if (ResultCodeEnum.SYSTEM_ERROR.getCode().equals(result.getCode())) {
                return ResultCodeEnum.SYSTEM_ERROR;
            }
        }
        return ResultCodeEnum.DATA_ERROR;
    }

    @Transactional
    @Override
    public void updateOrderStatus(String orderNo, Integer payType) {
        Date paymentTime = new Date();
        int updated = orderInfoMapper.markPaid(orderNo, payType, paymentTime);
        OrderInfo orderInfo = orderInfoMapper.getByOrderNo(orderNo);
        if (orderInfo == null) {
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }
        if (updated == 0) {
            if (orderInfo.getOrderStatus() != null && orderInfo.getOrderStatus() > 0) {
                return;
            }
            throw new MyException(ResultCodeEnum.DATA_ERROR);
        }

        OrderLog orderLog = new OrderLog();
        orderLog.setOrderId(orderInfo.getId());
        orderLog.setProcessStatus(1);
        orderLog.setNote("支付宝支付成功");
        orderLogMapper.save(orderLog);
    }
}
