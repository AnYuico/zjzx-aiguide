package com.tzp.zjzx.mq;

public final class MqEventIds {

    public static String paymentSucceeded(String orderNo) {
        return "payment.succeeded:" + orderNo;
    }

    public static String orderTimeout(String orderNo) {
        return "order.timeout:" + orderNo;
    }

    public static String inventoryConfirm(String orderNo) {
        return "inventory.confirm:" + orderNo;
    }

    public static String inventoryRelease(String orderNo) {
        return "inventory.release:" + orderNo;
    }

    public static String inventoryCompleted(String orderNo, Integer operationType) {
        return "inventory.completed:" + operationType + ":" + orderNo;
    }

    public static String orderPaid(String orderNo) {
        return "order.paid:" + orderNo;
    }

    public static String cartCleanup(String orderNo) {
        return "cart.cleanup:" + orderNo;
    }

    private MqEventIds() {
    }
}
