-- Repair inventory reservation tables required by service-product.
-- Select db_zjzx before running this script. Safe to run repeatedly.

CREATE TABLE IF NOT EXISTS inventory_request (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS inventory_reservation (
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

SHOW CREATE TABLE inventory_request;
SHOW CREATE TABLE inventory_reservation;
