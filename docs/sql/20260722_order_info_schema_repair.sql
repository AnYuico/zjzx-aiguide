-- Repair order_info columns and indexes required by idempotent submission
-- and timeout reconciliation. Select db_zjzx before running this script.

SET @schema_name = DATABASE();

SELECT IF(COUNT(*) = 0,
          'ALTER TABLE order_info ADD COLUMN request_id VARCHAR(64) NULL AFTER order_no',
          'SELECT ''order_info.request_id already exists''')
INTO @ddl
FROM information_schema.columns
WHERE table_schema = @schema_name
  AND table_name = 'order_info'
  AND column_name = 'request_id';
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT IF(COUNT(*) = 0,
          'ALTER TABLE order_info ADD COLUMN expire_time DATETIME NULL AFTER payment_time',
          'SELECT ''order_info.expire_time already exists''')
INTO @ddl
FROM information_schema.columns
WHERE table_schema = @schema_name
  AND table_name = 'order_info'
  AND column_name = 'expire_time';
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE order_info
SET request_id = CONCAT('legacy-', id)
WHERE request_id IS NULL OR request_id = '';

ALTER TABLE order_info
    MODIFY request_id VARCHAR(64) NOT NULL;

SELECT IF(COUNT(*) = 0,
          'ALTER TABLE order_info ADD UNIQUE KEY uk_order_info_order_no(order_no)',
          'SELECT ''uk_order_info_order_no already exists''')
INTO @ddl
FROM information_schema.statistics
WHERE table_schema = @schema_name
  AND table_name = 'order_info'
  AND index_name = 'uk_order_info_order_no';
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT IF(COUNT(*) = 0,
          'ALTER TABLE order_info ADD UNIQUE KEY uk_order_info_request_id(request_id)',
          'SELECT ''uk_order_info_request_id already exists''')
INTO @ddl
FROM information_schema.statistics
WHERE table_schema = @schema_name
  AND table_name = 'order_info'
  AND index_name = 'uk_order_info_request_id';
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT IF(COUNT(*) = 0,
          'ALTER TABLE order_info ADD KEY idx_order_info_timeout(order_status, expire_time)',
          'SELECT ''idx_order_info_timeout already exists''')
INTO @ddl
FROM information_schema.statistics
WHERE table_schema = @schema_name
  AND table_name = 'order_info'
  AND index_name = 'idx_order_info_timeout';
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SHOW COLUMNS FROM order_info WHERE Field IN ('request_id', 'expire_time');
SHOW INDEX FROM order_info
WHERE Key_name IN (
    'uk_order_info_order_no',
    'uk_order_info_request_id',
    'idx_order_info_timeout'
);
