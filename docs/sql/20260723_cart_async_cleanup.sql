-- Cart cleanup event support. Apply once to db_zjzx before starting service-order.
-- Legacy orders are marked as cart orders because their original source was not persisted.

SET @schema_name = DATABASE();

SELECT IF(COUNT(*) = 0,
          'ALTER TABLE order_info ADD COLUMN order_source TINYINT NOT NULL DEFAULT 1 COMMENT ''1-cart 2-buy-now'' AFTER request_id',
          'SELECT ''order_info.order_source already exists''')
INTO @ddl
FROM information_schema.columns
WHERE table_schema = @schema_name
  AND table_name = 'order_info'
  AND column_name = 'order_source';
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE order_info
SET order_source = 1
WHERE order_source IS NULL
   OR order_source NOT IN (1, 2);

ALTER TABLE order_info
    MODIFY order_source TINYINT NOT NULL DEFAULT 1 COMMENT '1-cart 2-buy-now';

SELECT column_name, column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = @schema_name
  AND table_name = 'order_info'
  AND column_name = 'order_source';

SELECT COUNT(*) AS invalid_order_source_count
FROM order_info
WHERE order_source NOT IN (1, 2)
   OR order_source IS NULL;
