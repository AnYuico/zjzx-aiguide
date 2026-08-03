-- Add user-side order visibility without changing the business order status.
-- Select db_zjzx before running this idempotent MySQL 8.0+ migration.

SET @schema_name = DATABASE();

SELECT IF(COUNT(*) = 0,
          'ALTER TABLE order_info ADD COLUMN user_deleted TINYINT NOT NULL DEFAULT 0 COMMENT ''user-side logical deletion: 0-visible, 1-hidden'' AFTER is_deleted',
          'SELECT ''order_info.user_deleted already exists''')
INTO @ddl
FROM information_schema.columns
WHERE table_schema = @schema_name
  AND table_name = 'order_info'
  AND column_name = 'user_deleted';
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SELECT IF(COUNT(*) = 0,
          'ALTER TABLE order_info ADD KEY idx_order_info_user_visible(user_id, user_deleted, id)',
          'SELECT ''idx_order_info_user_visible already exists''')
INTO @ddl
FROM information_schema.statistics
WHERE table_schema = @schema_name
  AND table_name = 'order_info'
  AND index_name = 'idx_order_info_user_visible';
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SHOW COLUMNS FROM order_info WHERE Field = 'user_deleted';
SHOW INDEX FROM order_info WHERE Key_name = 'idx_order_info_user_visible';
