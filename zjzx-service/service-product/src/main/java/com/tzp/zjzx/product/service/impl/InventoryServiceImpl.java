package com.tzp.zjzx.product.service.impl;

import com.tzp.zjzx.common.exception.MyException;
import com.tzp.zjzx.model.dto.product.StockItemDto;
import com.tzp.zjzx.model.dto.product.StockReserveRequest;
import com.tzp.zjzx.model.vo.common.ResultCodeEnum;
import com.tzp.zjzx.product.service.InventoryService;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class InventoryServiceImpl implements InventoryService {

    private static final int MAX_TRANSACTION_ATTEMPTS = 3;

    private final InventoryTransactionService transactionService;

    public InventoryServiceImpl(InventoryTransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @Override
    public void reserveStock(StockReserveRequest request) {
        StockReserveRequest normalized = normalize(request);
        String itemsHash = hashItems(normalized.getItems());
        executeWithRetry(() -> transactionService.reserveStock(normalized, itemsHash));
    }

    @Override
    public void confirmStock(String orderNo) {
        requireOrderNo(orderNo);
        executeWithRetry(() -> transactionService.confirmStock(orderNo));
    }

    @Override
    public void releaseStock(String orderNo) {
        requireOrderNo(orderNo);
        executeWithRetry(() -> transactionService.releaseStock(orderNo));
    }

    private StockReserveRequest normalize(StockReserveRequest request) {
        if (request == null || !StringUtils.hasText(request.getOrderNo())
                || CollectionUtils.isEmpty(request.getItems())) {
            throw new MyException(ResultCodeEnum.STOCK_REQUEST_INVALID);
        }

        Map<Long, Integer> quantities = new TreeMap<>();
        try {
            for (StockItemDto item : request.getItems()) {
                if (item == null || item.getSkuId() == null
                        || item.getSkuNum() == null || item.getSkuNum() <= 0) {
                    throw new MyException(ResultCodeEnum.STOCK_REQUEST_INVALID);
                }
                quantities.merge(item.getSkuId(), item.getSkuNum(), Math::addExact);
            }
        } catch (ArithmeticException ex) {
            throw new MyException(ResultCodeEnum.STOCK_REQUEST_INVALID);
        }

        List<StockItemDto> items = new ArrayList<>();
        quantities.forEach((skuId, skuNum) -> items.add(new StockItemDto(skuId, skuNum)));
        StockReserveRequest normalized = new StockReserveRequest();
        normalized.setOrderNo(request.getOrderNo());
        normalized.setItems(items);
        return normalized;
    }

    private String hashItems(List<StockItemDto> items) {
        StringBuilder canonical = new StringBuilder();
        items.forEach(item -> canonical.append(item.getSkuId())
                .append(':').append(item.getSkuNum()).append(';'));
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(
                    canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private void requireOrderNo(String orderNo) {
        if (!StringUtils.hasText(orderNo)) {
            throw new MyException(ResultCodeEnum.STOCK_REQUEST_INVALID);
        }
    }

    private void executeWithRetry(Runnable operation) {
        for (int attempt = 1; attempt <= MAX_TRANSACTION_ATTEMPTS; attempt++) {
            try {
                operation.run();
                return;
            } catch (TransientDataAccessException ex) {
                if (attempt == MAX_TRANSACTION_ATTEMPTS) {
                    throw ex;
                }
                sleepBeforeRetry(attempt);
            }
        }
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            long delay = ThreadLocalRandom.current().nextLong(20L, 61L) * attempt;
            Thread.sleep(delay);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying inventory transaction", ex);
        }
    }
}
