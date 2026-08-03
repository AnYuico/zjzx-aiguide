-- Schema integrity audit for the stock-reservation, RabbitMQ and cart-cleanup upgrades.
-- MySQL 8.0+. This script only reads metadata/business data and changes no rows.
-- Select db_zjzx before running it. Review every FAIL and SKIPPED result.

SELECT 'database' AS check_group,
       DATABASE() AS object_name,
       'db_zjzx' AS expected_value,
       IF(DATABASE() = 'db_zjzx', 'PASS', 'FAIL') AS status;

-- 1. Required tables and transactional engine.
WITH expected_tables AS (
    SELECT 'product_sku' AS table_name UNION ALL
    SELECT 'order_info' UNION ALL
    SELECT 'payment_info' UNION ALL
    SELECT 'order_statistics' UNION ALL
    SELECT 'order_submit_request' UNION ALL
    SELECT 'inventory_request' UNION ALL
    SELECT 'inventory_reservation' UNION ALL
    SELECT 'mq_outbox' UNION ALL
    SELECT 'mq_consume_log' UNION ALL
    SELECT 'inventory_operation_task' UNION ALL
    SELECT 'payment_exception_task'
)
SELECT 'table' AS check_group,
       e.table_name AS object_name,
       'InnoDB' AS expected_value,
       COALESCE(t.engine, 'MISSING') AS actual_value,
       CASE
           WHEN t.table_name IS NULL THEN 'FAIL'
           WHEN UPPER(t.engine) <> 'INNODB' THEN 'FAIL'
           ELSE 'PASS'
       END AS status
FROM expected_tables e
LEFT JOIN information_schema.tables t
       ON t.table_schema = DATABASE()
      AND t.table_name = e.table_name
ORDER BY e.table_name;

-- 2. Columns introduced or fully owned by these upgrades.
WITH expected_columns AS (
    SELECT 'product_sku' table_name, 'stock_num' column_name, 'int' column_type, 'NO' nullable UNION ALL
    SELECT 'order_info', 'request_id', 'varchar(64)', 'NO' UNION ALL
    SELECT 'order_info', 'order_source', 'tinyint', 'NO' UNION ALL
    SELECT 'order_info', 'expire_time', 'datetime', 'YES' UNION ALL
    SELECT 'order_info', 'user_deleted', 'tinyint', 'NO' UNION ALL

    SELECT 'order_submit_request', 'id', 'bigint', 'NO' UNION ALL
    SELECT 'order_submit_request', 'request_id', 'varchar(64)', 'NO' UNION ALL
    SELECT 'order_submit_request', 'user_id', 'bigint', 'NO' UNION ALL
    SELECT 'order_submit_request', 'order_no', 'varchar(64)', 'NO' UNION ALL
    SELECT 'order_submit_request', 'status', 'tinyint', 'NO' UNION ALL
    SELECT 'order_submit_request', 'order_id', 'bigint', 'YES' UNION ALL
    SELECT 'order_submit_request', 'create_time', 'timestamp', 'NO' UNION ALL
    SELECT 'order_submit_request', 'update_time', 'timestamp', 'NO' UNION ALL
    SELECT 'order_submit_request', 'is_deleted', 'tinyint', 'NO' UNION ALL

    SELECT 'inventory_request', 'id', 'bigint', 'NO' UNION ALL
    SELECT 'inventory_request', 'order_no', 'varchar(64)', 'NO' UNION ALL
    SELECT 'inventory_request', 'items_hash', 'varchar(64)', 'NO' UNION ALL
    SELECT 'inventory_request', 'status', 'tinyint', 'NO' UNION ALL
    SELECT 'inventory_request', 'create_time', 'timestamp', 'NO' UNION ALL
    SELECT 'inventory_request', 'update_time', 'timestamp', 'NO' UNION ALL
    SELECT 'inventory_request', 'is_deleted', 'tinyint', 'NO' UNION ALL

    SELECT 'inventory_reservation', 'id', 'bigint', 'NO' UNION ALL
    SELECT 'inventory_reservation', 'order_no', 'varchar(64)', 'NO' UNION ALL
    SELECT 'inventory_reservation', 'sku_id', 'bigint', 'NO' UNION ALL
    SELECT 'inventory_reservation', 'sku_num', 'int', 'NO' UNION ALL
    SELECT 'inventory_reservation', 'status', 'tinyint', 'NO' UNION ALL
    SELECT 'inventory_reservation', 'create_time', 'timestamp', 'NO' UNION ALL
    SELECT 'inventory_reservation', 'update_time', 'timestamp', 'NO' UNION ALL
    SELECT 'inventory_reservation', 'is_deleted', 'tinyint', 'NO' UNION ALL

    SELECT 'mq_outbox', 'id', 'bigint', 'NO' UNION ALL
    SELECT 'mq_outbox', 'event_id', 'varchar(128)', 'NO' UNION ALL
    SELECT 'mq_outbox', 'producer', 'varchar(64)', 'NO' UNION ALL
    SELECT 'mq_outbox', 'event_type', 'varchar(64)', 'NO' UNION ALL
    SELECT 'mq_outbox', 'exchange_name', 'varchar(128)', 'NO' UNION ALL
    SELECT 'mq_outbox', 'routing_key', 'varchar(128)', 'NO' UNION ALL
    SELECT 'mq_outbox', 'payload', 'longtext', 'NO' UNION ALL
    SELECT 'mq_outbox', 'status', 'tinyint', 'NO' UNION ALL
    SELECT 'mq_outbox', 'retry_count', 'int', 'NO' UNION ALL
    SELECT 'mq_outbox', 'next_retry_time', 'datetime', 'NO' UNION ALL
    SELECT 'mq_outbox', 'deliver_at', 'datetime', 'YES' UNION ALL
    SELECT 'mq_outbox', 'sent_time', 'datetime', 'YES' UNION ALL
    SELECT 'mq_outbox', 'last_error', 'varchar(1000)', 'YES' UNION ALL
    SELECT 'mq_outbox', 'create_time', 'timestamp', 'NO' UNION ALL
    SELECT 'mq_outbox', 'update_time', 'timestamp', 'NO' UNION ALL

    SELECT 'mq_consume_log', 'id', 'bigint', 'NO' UNION ALL
    SELECT 'mq_consume_log', 'consumer_name', 'varchar(128)', 'NO' UNION ALL
    SELECT 'mq_consume_log', 'event_id', 'varchar(128)', 'NO' UNION ALL
    SELECT 'mq_consume_log', 'event_type', 'varchar(64)', 'NO' UNION ALL
    SELECT 'mq_consume_log', 'consume_time', 'timestamp', 'NO' UNION ALL

    SELECT 'inventory_operation_task', 'id', 'bigint', 'NO' UNION ALL
    SELECT 'inventory_operation_task', 'order_no', 'varchar(64)', 'NO' UNION ALL
    SELECT 'inventory_operation_task', 'operation_type', 'tinyint', 'NO' UNION ALL
    SELECT 'inventory_operation_task', 'status', 'tinyint', 'NO' UNION ALL
    SELECT 'inventory_operation_task', 'retry_count', 'int', 'NO' UNION ALL
    SELECT 'inventory_operation_task', 'next_retry_time', 'datetime', 'NO' UNION ALL
    SELECT 'inventory_operation_task', 'last_error', 'varchar(500)', 'YES' UNION ALL
    SELECT 'inventory_operation_task', 'create_time', 'timestamp', 'NO' UNION ALL
    SELECT 'inventory_operation_task', 'update_time', 'timestamp', 'NO' UNION ALL
    SELECT 'inventory_operation_task', 'is_deleted', 'tinyint', 'NO' UNION ALL

    SELECT 'payment_exception_task', 'id', 'bigint', 'NO' UNION ALL
    SELECT 'payment_exception_task', 'event_id', 'varchar(128)', 'NO' UNION ALL
    SELECT 'payment_exception_task', 'order_no', 'varchar(64)', 'NO' UNION ALL
    SELECT 'payment_exception_task', 'trade_no', 'varchar(128)', 'YES' UNION ALL
    SELECT 'payment_exception_task', 'amount', 'decimal(10,2)', 'YES' UNION ALL
    SELECT 'payment_exception_task', 'reason', 'varchar(64)', 'NO' UNION ALL
    SELECT 'payment_exception_task', 'status', 'tinyint', 'NO' UNION ALL
    SELECT 'payment_exception_task', 'create_time', 'timestamp', 'NO' UNION ALL
    SELECT 'payment_exception_task', 'update_time', 'timestamp', 'NO' UNION ALL
    SELECT 'payment_exception_task', 'is_deleted', 'tinyint', 'NO'
)
SELECT 'column' AS check_group,
       CONCAT(e.table_name, '.', e.column_name) AS object_name,
       CONCAT(e.column_type, ' nullable=', e.nullable) AS expected_value,
       CASE
           WHEN c.column_name IS NULL THEN 'MISSING'
           ELSE CONCAT(LOWER(c.column_type), ' nullable=', c.is_nullable)
       END AS actual_value,
       CASE
           WHEN c.column_name IS NULL THEN 'FAIL'
           WHEN LOWER(c.column_type) <> e.column_type THEN 'FAIL'
           WHEN c.is_nullable <> e.nullable THEN 'FAIL'
           ELSE 'PASS'
       END AS status
FROM expected_columns e
LEFT JOIN information_schema.columns c
       ON c.table_schema = DATABASE()
      AND c.table_name = e.table_name
      AND c.column_name = e.column_name
ORDER BY e.table_name, e.column_name;

-- 3. Defaults required by conditional stock and state updates.
SELECT 'column_default' AS check_group,
       'product_sku.stock_num' AS object_name,
       '0' AS expected_value,
       COALESCE(c.column_default, 'NULL') AS actual_value,
       IF(c.column_name IS NOT NULL AND c.column_default = '0', 'PASS', 'FAIL') AS status
FROM (SELECT 1) seed
LEFT JOIN information_schema.columns c
       ON c.table_schema = DATABASE()
      AND c.table_name = 'product_sku'
      AND c.column_name = 'stock_num';

SELECT 'column_default' AS check_group,
       'order_info.user_deleted' AS object_name,
       '0' AS expected_value,
       COALESCE(c.column_default, 'NULL') AS actual_value,
       IF(c.column_name IS NOT NULL AND c.column_default = '0', 'PASS', 'FAIL') AS status
FROM (SELECT 1) seed
LEFT JOIN information_schema.columns c
       ON c.table_schema = DATABASE()
      AND c.table_name = 'order_info'
      AND c.column_name = 'user_deleted';

SELECT 'column_default' AS check_group,
       'order_info.order_source' AS object_name,
       '1' AS expected_value,
       COALESCE(c.column_default, 'NULL') AS actual_value,
       IF(c.column_name IS NOT NULL AND c.column_default = '1', 'PASS', 'FAIL') AS status
FROM (SELECT 1) seed
LEFT JOIN information_schema.columns c
       ON c.table_schema = DATABASE()
      AND c.table_name = 'order_info'
      AND c.column_name = 'order_source';

-- 4. Required indexes, including exact column order and uniqueness.
WITH expected_indexes AS (
    SELECT 'order_info' table_name, 'uk_order_info_order_no' index_name, 0 non_unique, 'order_no' columns_list UNION ALL
    SELECT 'order_info', 'uk_order_info_request_id', 0, 'request_id' UNION ALL
    SELECT 'order_info', 'idx_order_info_timeout', 1, 'order_status,expire_time' UNION ALL
    SELECT 'order_info', 'idx_order_info_payment', 1, 'order_status,payment_time' UNION ALL
    SELECT 'order_info', 'idx_order_info_user_visible', 1, 'user_id,user_deleted,id' UNION ALL
    SELECT 'payment_info', 'uk_payment_info_order_no', 0, 'order_no' UNION ALL
    SELECT 'order_statistics', 'uk_order_statistics_order_date', 0, 'order_date' UNION ALL

    SELECT 'order_submit_request', 'PRIMARY', 0, 'id' UNION ALL
    SELECT 'order_submit_request', 'uk_order_submit_request_id', 0, 'request_id' UNION ALL
    SELECT 'order_submit_request', 'uk_order_submit_order_no', 0, 'order_no' UNION ALL

    SELECT 'inventory_request', 'PRIMARY', 0, 'id' UNION ALL
    SELECT 'inventory_request', 'uk_inventory_request_order_no', 0, 'order_no' UNION ALL
    SELECT 'inventory_request', 'idx_inventory_request_status_time', 1, 'status,update_time' UNION ALL

    SELECT 'inventory_reservation', 'PRIMARY', 0, 'id' UNION ALL
    SELECT 'inventory_reservation', 'uk_inventory_reservation_order_sku', 0, 'order_no,sku_id' UNION ALL
    SELECT 'inventory_reservation', 'idx_inventory_reservation_status_time', 1, 'status,update_time' UNION ALL

    SELECT 'mq_outbox', 'PRIMARY', 0, 'id' UNION ALL
    SELECT 'mq_outbox', 'uk_mq_outbox_event_id', 0, 'event_id' UNION ALL
    SELECT 'mq_outbox', 'idx_mq_outbox_publish', 1, 'producer,status,next_retry_time' UNION ALL

    SELECT 'mq_consume_log', 'PRIMARY', 0, 'id' UNION ALL
    SELECT 'mq_consume_log', 'uk_mq_consumer_event', 0, 'consumer_name,event_id' UNION ALL
    SELECT 'mq_consume_log', 'idx_mq_consume_event', 1, 'event_id' UNION ALL

    SELECT 'inventory_operation_task', 'PRIMARY', 0, 'id' UNION ALL
    SELECT 'inventory_operation_task', 'uk_inventory_operation_order_type', 0, 'order_no,operation_type' UNION ALL
    SELECT 'inventory_operation_task', 'idx_inventory_operation_retry', 1, 'status,next_retry_time' UNION ALL

    SELECT 'payment_exception_task', 'PRIMARY', 0, 'id' UNION ALL
    SELECT 'payment_exception_task', 'uk_payment_exception_event', 0, 'event_id' UNION ALL
    SELECT 'payment_exception_task', 'idx_payment_exception_status', 1, 'status,create_time'
), actual_indexes AS (
    SELECT table_name,
           index_name,
           non_unique,
           GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_list
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
    GROUP BY table_name, index_name, non_unique
)
SELECT 'index' AS check_group,
       CONCAT(e.table_name, '.', e.index_name) AS object_name,
       CONCAT(IF(e.non_unique = 0, 'UNIQUE ', ''), e.columns_list) AS expected_value,
       CASE
           WHEN a.index_name IS NULL THEN 'MISSING'
           ELSE CONCAT(IF(a.non_unique = 0, 'UNIQUE ', ''), a.columns_list)
       END AS actual_value,
       CASE
           WHEN a.index_name IS NULL THEN 'FAIL'
           WHEN a.non_unique <> e.non_unique THEN 'FAIL'
           WHEN a.columns_list <> e.columns_list THEN 'FAIL'
           ELSE 'PASS'
       END AS status
FROM expected_indexes e
LEFT JOIN actual_indexes a
       ON a.table_name = e.table_name
      AND a.index_name = e.index_name
ORDER BY e.table_name, e.index_name;

-- 5. Existing data checks. Missing prerequisite tables return SKIPPED.
SET @has_product_sku = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'product_sku'
);
SET @sql = IF(
    @has_product_sku = 1,
    'SELECT ''data'' check_group, ''product_sku.invalid_stock'' object_name, COUNT(*) violations, IF(COUNT(*)=0,''PASS'',''FAIL'') status FROM product_sku WHERE stock_num IS NULL OR stock_num < 0',
    'SELECT ''data'' check_group, ''product_sku.invalid_stock'' object_name, NULL violations, ''SKIPPED'' status'
);
PREPARE integrity_stmt FROM @sql;
EXECUTE integrity_stmt;
DEALLOCATE PREPARE integrity_stmt;

SET @has_order_info = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'order_info'
);
SET @has_order_request_id = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'order_info' AND column_name = 'request_id'
);
SET @sql = IF(
    @has_order_info = 1 AND @has_order_request_id = 1,
    'SELECT ''data'' check_group, ''order_info.empty_request_id'' object_name, COUNT(*) violations, IF(COUNT(*)=0,''PASS'',''FAIL'') status FROM order_info WHERE request_id IS NULL OR request_id='''' UNION ALL SELECT ''data'', ''order_info.duplicate_order_no'', COUNT(*), IF(COUNT(*)=0,''PASS'',''FAIL'') FROM (SELECT order_no FROM order_info GROUP BY order_no HAVING COUNT(*)>1) d UNION ALL SELECT ''data'', ''order_info.duplicate_request_id'', COUNT(*), IF(COUNT(*)=0,''PASS'',''FAIL'') FROM (SELECT request_id FROM order_info GROUP BY request_id HAVING COUNT(*)>1) d',
    'SELECT ''data'' check_group, ''order_info.idempotency_data'' object_name, NULL violations, ''SKIPPED'' status'
);
PREPARE integrity_stmt FROM @sql;
EXECUTE integrity_stmt;
DEALLOCATE PREPARE integrity_stmt;

SET @has_order_user_deleted = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'order_info'
      AND column_name = 'user_deleted'
);
SET @sql = IF(
    @has_order_user_deleted = 1,
    'SELECT ''data'' check_group, ''order_info.invalid_user_deleted'' object_name, COUNT(*) violations, IF(COUNT(*)=0,''PASS'',''FAIL'') status FROM order_info WHERE user_deleted NOT IN (0,1)',
    'SELECT ''data'' check_group, ''order_info.invalid_user_deleted'' object_name, NULL violations, ''SKIPPED'' status'
);
PREPARE integrity_stmt FROM @sql;
EXECUTE integrity_stmt;
DEALLOCATE PREPARE integrity_stmt;

SET @has_order_source = (
    SELECT COUNT(*) FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'order_info'
      AND column_name = 'order_source'
);
SET @sql = IF(
    @has_order_source = 1,
    'SELECT ''data'' check_group, ''order_info.invalid_order_source'' object_name, COUNT(*) violations, IF(COUNT(*)=0,''PASS'',''FAIL'') status FROM order_info WHERE order_source NOT IN (1,2) OR order_source IS NULL',
    'SELECT ''data'' check_group, ''order_info.invalid_order_source'' object_name, NULL violations, ''SKIPPED'' status'
);
PREPARE integrity_stmt FROM @sql;
EXECUTE integrity_stmt;
DEALLOCATE PREPARE integrity_stmt;

SET @has_payment_info = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'payment_info'
);
SET @sql = IF(
    @has_payment_info = 1,
    'SELECT ''data'' check_group, ''payment_info.duplicate_order_no'' object_name, COUNT(*) violations, IF(COUNT(*)=0,''PASS'',''FAIL'') status FROM (SELECT order_no FROM payment_info GROUP BY order_no HAVING COUNT(*)>1) d',
    'SELECT ''data'' check_group, ''payment_info.duplicate_order_no'' object_name, NULL violations, ''SKIPPED'' status'
);
PREPARE integrity_stmt FROM @sql;
EXECUTE integrity_stmt;
DEALLOCATE PREPARE integrity_stmt;

SET @has_order_statistics = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'order_statistics'
);
SET @sql = IF(
    @has_order_statistics = 1,
    'SELECT ''data'' check_group, ''order_statistics.duplicate_order_date'' object_name, COUNT(*) violations, IF(COUNT(*)=0,''PASS'',''FAIL'') status FROM (SELECT order_date FROM order_statistics GROUP BY order_date HAVING COUNT(*)>1) d',
    'SELECT ''data'' check_group, ''order_statistics.duplicate_order_date'' object_name, NULL violations, ''SKIPPED'' status'
);
PREPARE integrity_stmt FROM @sql;
EXECUTE integrity_stmt;
DEALLOCATE PREPARE integrity_stmt;

SET @inventory_table_count = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN ('inventory_request', 'inventory_reservation')
);
SET @sql = IF(
    @inventory_table_count = 2,
    'SELECT ''data'' check_group, ''inventory_request.invalid_status'' object_name, COUNT(*) violations, IF(COUNT(*)=0,''PASS'',''FAIL'') status FROM inventory_request WHERE status NOT IN (0,1,2,3) OR is_deleted NOT IN (0,1) UNION ALL SELECT ''data'', ''inventory_reservation.invalid_status_or_quantity'', COUNT(*), IF(COUNT(*)=0,''PASS'',''FAIL'') FROM inventory_reservation WHERE status NOT IN (0,1,2) OR sku_num <= 0 OR is_deleted NOT IN (0,1) UNION ALL SELECT ''data'', ''inventory_reservation.orphan_request'', COUNT(*), IF(COUNT(*)=0,''PASS'',''FAIL'') FROM inventory_reservation r LEFT JOIN inventory_request q ON q.order_no=r.order_no AND q.is_deleted=0 WHERE r.is_deleted=0 AND q.id IS NULL UNION ALL SELECT ''data'', ''inventory_request.reserved_without_items'', COUNT(*), IF(COUNT(*)=0,''PASS'',''FAIL'') FROM (SELECT q.id FROM inventory_request q LEFT JOIN inventory_reservation r ON r.order_no=q.order_no AND r.is_deleted=0 WHERE q.is_deleted=0 AND q.status IN (1,2) GROUP BY q.id HAVING COUNT(r.id)=0) d',
    'SELECT ''data'' check_group, ''inventory.state_machine'' object_name, NULL violations, ''SKIPPED'' status'
);
PREPARE integrity_stmt FROM @sql;
EXECUTE integrity_stmt;
DEALLOCATE PREPARE integrity_stmt;

SET @mq_table_count = (
    SELECT COUNT(*) FROM information_schema.tables
    WHERE table_schema = DATABASE()
      AND table_name IN (
          'mq_outbox',
          'mq_consume_log',
          'inventory_operation_task',
          'payment_exception_task'
      )
);
SET @sql = IF(
    @mq_table_count = 4,
    'SELECT ''data'' check_group, ''mq_outbox.invalid_state'' object_name, COUNT(*) violations, IF(COUNT(*)=0,''PASS'',''FAIL'') status FROM mq_outbox WHERE status NOT IN (0,1,2) OR retry_count < 0 UNION ALL SELECT ''data'', ''mq_consume_log.duplicate_consume'', COUNT(*), IF(COUNT(*)=0,''PASS'',''FAIL'') FROM (SELECT consumer_name,event_id FROM mq_consume_log GROUP BY consumer_name,event_id HAVING COUNT(*)>1) d UNION ALL SELECT ''data'', ''inventory_operation_task.invalid_state'', COUNT(*), IF(COUNT(*)=0,''PASS'',''FAIL'') FROM inventory_operation_task WHERE operation_type NOT IN (1,2) OR status NOT IN (0,1,2) OR retry_count < 0 OR is_deleted NOT IN (0,1) UNION ALL SELECT ''data'', ''payment_exception_task.invalid_state'', COUNT(*), IF(COUNT(*)=0,''PASS'',''FAIL'') FROM payment_exception_task WHERE reason NOT IN (''LATE_PAYMENT'',''AMOUNT_MISMATCH'') OR status NOT IN (0,1) OR is_deleted NOT IN (0,1)',
    'SELECT ''data'' check_group, ''rabbitmq.state_tables'' object_name, NULL violations, ''SKIPPED'' status'
);
PREPARE integrity_stmt FROM @sql;
EXECUTE integrity_stmt;
DEALLOCATE PREPARE integrity_stmt;

-- PASS: compliant. FAIL: must be repaired. SKIPPED: prerequisite table/column is missing.
