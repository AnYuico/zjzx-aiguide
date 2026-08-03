package com.tzp.zjzx.mq;

public final class RabbitMqConstants {

    public static final String ORDER_EVENT_EXCHANGE = "zjzx.order.events";
    public static final String ORDER_DEAD_EXCHANGE = "zjzx.order.dlx";
    public static final String SECKILL_EVENT_EXCHANGE = "zjzx.seckill.events";
    public static final String SECKILL_DEAD_EXCHANGE = "zjzx.seckill.dlx";

    public static final String PAYMENT_SUCCEEDED = "payment.succeeded";
    public static final String ORDER_TIMEOUT_DELAY = "order.timeout.delay";
    public static final String ORDER_TIMEOUT_CHECK = "order.timeout.check";
    public static final String INVENTORY_CONFIRM_REQUESTED = "inventory.confirm.requested";
    public static final String INVENTORY_RELEASE_REQUESTED = "inventory.release.requested";
    public static final String INVENTORY_OPERATION_COMPLETED = "inventory.operation.completed";
    public static final String ORDER_PAID = "order.paid";
    public static final String CART_CLEANUP_REQUESTED = "cart.cleanup.requested";
    public static final String SECKILL_ORDER_REQUESTED = "seckill.order.requested";

    public static final String ORDER_PAYMENT_QUEUE = "zjzx.order.payment-succeeded";
    public static final String ORDER_TIMEOUT_DELAY_QUEUE = "zjzx.order.timeout-delay";
    public static final String ORDER_TIMEOUT_QUEUE = "zjzx.order.timeout-check";
    public static final String ORDER_INVENTORY_COMPLETED_QUEUE = "zjzx.order.inventory-completed";
    public static final String PRODUCT_INVENTORY_CONFIRM_QUEUE = "zjzx.product.inventory-confirm";
    public static final String PRODUCT_INVENTORY_RELEASE_QUEUE = "zjzx.product.inventory-release";
    public static final String MANAGER_ORDER_PAID_QUEUE = "zjzx.manager.order-paid";
    public static final String CART_CLEANUP_QUEUE = "zjzx.cart.cleanup";
    public static final String SECKILL_ORDER_QUEUE = "zjzx.order.seckill-create";

    public static final String ORDER_PAYMENT_DEAD = "order.payment-succeeded.dead";
    public static final String ORDER_TIMEOUT_DEAD = "order.timeout-check.dead";
    public static final String ORDER_INVENTORY_COMPLETED_DEAD = "order.inventory-completed.dead";
    public static final String PRODUCT_INVENTORY_CONFIRM_DEAD = "product.inventory-confirm.dead";
    public static final String PRODUCT_INVENTORY_RELEASE_DEAD = "product.inventory-release.dead";
    public static final String MANAGER_ORDER_PAID_DEAD = "manager.order-paid.dead";
    public static final String CART_CLEANUP_DEAD = "cart.cleanup.dead";
    public static final String SECKILL_ORDER_DEAD = "seckill.order.requested.dead";

    public static final String PAYMENT_SUCCEEDED_EVENT = "PAYMENT_SUCCEEDED";
    public static final String ORDER_TIMEOUT_EVENT = "ORDER_TIMEOUT";
    public static final String INVENTORY_CONFIRM_EVENT = "INVENTORY_CONFIRM_REQUESTED";
    public static final String INVENTORY_RELEASE_EVENT = "INVENTORY_RELEASE_REQUESTED";
    public static final String INVENTORY_COMPLETED_EVENT = "INVENTORY_OPERATION_COMPLETED";
    public static final String ORDER_PAID_EVENT = "ORDER_PAID";
    public static final String CART_CLEANUP_EVENT = "CART_CLEANUP_REQUESTED";
    public static final String SECKILL_ORDER_EVENT = "SECKILL_ORDER_REQUESTED";

    public static final String ORDER_PAYMENT_CONSUMER = "service-order:payment-succeeded";
    public static final String ORDER_TIMEOUT_CONSUMER = "service-order:timeout-check";
    public static final String ORDER_INVENTORY_COMPLETED_CONSUMER = "service-order:inventory-completed";
    public static final String PRODUCT_INVENTORY_CONFIRM_CONSUMER = "service-product:inventory-confirm";
    public static final String PRODUCT_INVENTORY_RELEASE_CONSUMER = "service-product:inventory-release";
    public static final String MANAGER_ORDER_PAID_CONSUMER = "server-manager:order-paid";
    public static final String CART_CLEANUP_CONSUMER = "service-cart:cleanup";
    public static final String SECKILL_ORDER_CONSUMER = "service-order:seckill-create";

    private RabbitMqConstants() {
    }
}
