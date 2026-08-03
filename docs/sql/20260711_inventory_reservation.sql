-- Run after checking existing duplicate order numbers and payment records.
-- Configure the same non-empty zjzx.internal-api.token in user, product, order and pay services.

UPDATE product_sku SET stock_num = 0 WHERE stock_num IS NULL OR stock_num < 0;

ALTER TABLE product_sku
    MODIFY stock_num INT NOT NULL DEFAULT 0;

ALTER TABLE order_info
    ADD COLUMN request_id VARCHAR(64) NULL COMMENT 'order submit idempotency key' AFTER order_no,
    ADD COLUMN expire_time DATETIME NULL COMMENT 'payment expiration time' AFTER payment_time;

UPDATE order_info
SET request_id = CONCAT('legacy-', id)
WHERE request_id IS NULL;

ALTER TABLE order_info
    MODIFY request_id VARCHAR(64) NOT NULL,
    ADD UNIQUE KEY uk_order_info_order_no(order_no),
    ADD UNIQUE KEY uk_order_info_request_id(request_id),
    ADD KEY idx_order_info_timeout(order_status, expire_time);

-- The data dictionary names the payment index uniq_order_no but marks it as
-- a NORMAL index. Verify with SHOW INDEX before replacing it in production.
ALTER TABLE payment_info
    DROP INDEX uniq_order_no,
    ADD UNIQUE KEY uk_payment_info_order_no(order_no);

CREATE TABLE order_submit_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    request_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0-processing 1-success 2-failed',
    order_id BIGINT NULL,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_submit_request_id(request_id),
    UNIQUE KEY uk_order_submit_order_no(order_no)
);

CREATE TABLE inventory_request (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    items_hash VARCHAR(64) NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0-processing 1-reserved 2-confirmed 3-released',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_request_order_no(order_no),
    KEY idx_inventory_request_status_time(status, update_time)
);

CREATE TABLE inventory_reservation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_no VARCHAR(64) NOT NULL,
    sku_id BIGINT NOT NULL,
    sku_num INT NOT NULL,
    status TINYINT NOT NULL DEFAULT 0 COMMENT '0-reserved 1-confirmed 2-released',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_inventory_reservation_order_sku(order_no, sku_id),
    KEY idx_inventory_reservation_status_time(status, update_time)
);

-- Stock release retries are stored in inventory_operation_task after the RabbitMQ v1 upgrade.
-- See 20260722_rabbitmq_outbox.sql.
