package com.tzp.zjzx.product.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.dto.product.StockItemDto;
import com.tzp.zjzx.model.dto.product.StockReserveRequest;
import com.tzp.zjzx.model.entity.product.InventoryRequest;
import com.tzp.zjzx.model.entity.product.InventoryReservation;
import com.tzp.zjzx.model.enums.InventoryRequestStatus;
import com.tzp.zjzx.model.enums.InventoryReservationStatus;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.product.mapper.InventoryRequestMapper;
import com.tzp.zjzx.product.mapper.InventoryReservationMapper;
import com.tzp.zjzx.product.mapper.ProductSkuMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Objects;

@Service
public class InventoryTransactionService {

    private final ProductSkuMapper productSkuMapper;
    private final InventoryRequestMapper inventoryRequestMapper;
    private final InventoryReservationMapper inventoryReservationMapper;

    public InventoryTransactionService(ProductSkuMapper productSkuMapper,
                                       InventoryRequestMapper inventoryRequestMapper,
                                       InventoryReservationMapper inventoryReservationMapper) {
        this.productSkuMapper = productSkuMapper;
        this.inventoryRequestMapper = inventoryRequestMapper;
        this.inventoryReservationMapper = inventoryReservationMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void reserveStock(StockReserveRequest request, String itemsHash) {
        InventoryRequest newRequest = new InventoryRequest();
        newRequest.setOrderNo(request.getOrderNo());
        newRequest.setItemsHash(itemsHash);
        newRequest.setStatus(InventoryRequestStatus.PROCESSING.getCode());
        inventoryRequestMapper.insertIgnore(newRequest);

        InventoryRequest inventoryRequest = inventoryRequestMapper.selectByOrderNoForUpdate(request.getOrderNo());
        if (inventoryRequest == null || !Objects.equals(itemsHash, inventoryRequest.getItemsHash())) {
            throw new MyException(ResultCodeEnum.STOCK_REQUEST_INVALID);
        }

        if (Objects.equals(inventoryRequest.getStatus(), InventoryRequestStatus.RESERVED.getCode())
                || Objects.equals(inventoryRequest.getStatus(), InventoryRequestStatus.CONFIRMED.getCode())) {
            return;
        }
        if (Objects.equals(inventoryRequest.getStatus(), InventoryRequestStatus.RELEASED.getCode())
                || !Objects.equals(inventoryRequest.getStatus(), InventoryRequestStatus.PROCESSING.getCode())
                || inventoryReservationMapper.countByOrderNo(request.getOrderNo()) > 0) {
            throw new MyException(ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR);
        }

        for (StockItemDto item : request.getItems()) {
            int affectedRows = productSkuMapper.reserveStock(item.getSkuId(), item.getSkuNum());
            if (affectedRows != 1) {
                throw new MyException(ResultCodeEnum.STOCK_LESS);
            }

            InventoryReservation reservation = new InventoryReservation();
            reservation.setOrderNo(request.getOrderNo());
            reservation.setSkuId(item.getSkuId());
            reservation.setSkuNum(item.getSkuNum());
            reservation.setStatus(InventoryReservationStatus.RESERVED.getCode());
            inventoryReservationMapper.insert(reservation);
        }

        int updated = inventoryRequestMapper.updateStatus(
                request.getOrderNo(),
                InventoryRequestStatus.PROCESSING.getCode(),
                InventoryRequestStatus.RESERVED.getCode());
        if (updated != 1) {
            throw new MyException(ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void confirmStock(String orderNo) {
        InventoryRequest request = requireRequest(orderNo);
        if (Objects.equals(request.getStatus(), InventoryRequestStatus.CONFIRMED.getCode())) {
            return;
        }
        if (!Objects.equals(request.getStatus(), InventoryRequestStatus.RESERVED.getCode())) {
            throw new MyException(ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR);
        }

        List<InventoryReservation> reservations = inventoryReservationMapper.selectByOrderNoForUpdate(orderNo);
        if (CollectionUtils.isEmpty(reservations)) {
            throw new MyException(ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR);
        }

        for (InventoryReservation reservation : reservations) {
            int statusRows = inventoryReservationMapper.updateStatus(
                    orderNo,
                    reservation.getSkuId(),
                    InventoryReservationStatus.RESERVED.getCode(),
                    InventoryReservationStatus.CONFIRMED.getCode());
            if (statusRows != 1 || productSkuMapper.increaseSale(
                    reservation.getSkuId(), reservation.getSkuNum()) != 1) {
                throw new MyException(ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR);
            }
        }

        if (inventoryRequestMapper.updateStatus(
                orderNo,
                InventoryRequestStatus.RESERVED.getCode(),
                InventoryRequestStatus.CONFIRMED.getCode()) != 1) {
            throw new MyException(ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR);
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void releaseStock(String orderNo) {
        InventoryRequest request = requireRequest(orderNo);
        if (Objects.equals(request.getStatus(), InventoryRequestStatus.RELEASED.getCode())) {
            return;
        }
        if (!Objects.equals(request.getStatus(), InventoryRequestStatus.RESERVED.getCode())) {
            throw new MyException(ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR);
        }

        List<InventoryReservation> reservations = inventoryReservationMapper.selectByOrderNoForUpdate(orderNo);
        if (CollectionUtils.isEmpty(reservations)) {
            throw new MyException(ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR);
        }

        for (InventoryReservation reservation : reservations) {
            int statusRows = inventoryReservationMapper.updateStatus(
                    orderNo,
                    reservation.getSkuId(),
                    InventoryReservationStatus.RESERVED.getCode(),
                    InventoryReservationStatus.RELEASED.getCode());
            if (statusRows != 1 || productSkuMapper.restoreStock(
                    reservation.getSkuId(), reservation.getSkuNum()) != 1) {
                throw new MyException(ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR);
            }
        }

        if (inventoryRequestMapper.updateStatus(
                orderNo,
                InventoryRequestStatus.RESERVED.getCode(),
                InventoryRequestStatus.RELEASED.getCode()) != 1) {
            throw new MyException(ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR);
        }
    }

    private InventoryRequest requireRequest(String orderNo) {
        InventoryRequest request = inventoryRequestMapper.selectByOrderNoForUpdate(orderNo);
        if (request == null) {
            throw new MyException(ResultCodeEnum.STOCK_RESERVATION_STATE_ERROR);
        }
        return request;
    }
}
