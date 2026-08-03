-- Repairs the current FAIL results from 20260722_schema_integrity_check.sql.
-- Select db_zjzx before running this script. MySQL 8.0+.

-- Preflight evidence. The unique index applies to all rows, including soft-deleted rows.
SELECT id, sku_code, sku_name, stock_num, status, is_deleted
FROM product_sku
WHERE stock_num IS NULL OR stock_num < 0
ORDER BY id;

SELECT order_no, COUNT(*) AS duplicate_count
FROM payment_info
GROUP BY order_no
HAVING COUNT(*) > 1
ORDER BY duplicate_count DESC, order_no;

-- Normalize legacy invalid stock before adding the NOT NULL constraint.
UPDATE product_sku
SET stock_num = 0,
    update_time = NOW()
WHERE stock_num IS NULL OR stock_num < 0;

ALTER TABLE product_sku
    MODIFY stock_num INT NOT NULL DEFAULT 0;

-- Add or normalize the payment order-number unique index only when data is safe.
SET @payment_duplicate_groups = (
    SELECT COUNT(*)
    FROM (
        SELECT order_no
        FROM payment_info
        GROUP BY order_no
        HAVING COUNT(*) > 1
    ) duplicate_groups
);

SET @target_index_name_exists = (
    SELECT COUNT(DISTINCT index_name)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'payment_info'
      AND index_name = 'uk_payment_info_order_no'
);

SET @target_index_valid = (
    SELECT COUNT(*)
    FROM (
        SELECT index_name,
               non_unique,
               GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_list
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_info'
          AND index_name = 'uk_payment_info_order_no'
        GROUP BY index_name, non_unique
    ) indexes_found
    WHERE non_unique = 0
      AND columns_list = 'order_no'
);

SET @equivalent_unique_index = (
    SELECT MIN(index_name)
    FROM (
        SELECT index_name,
               non_unique,
               GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_list
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_info'
          AND index_name <> 'PRIMARY'
        GROUP BY index_name, non_unique
    ) indexes_found
    WHERE non_unique = 0
      AND columns_list = 'order_no'
);

SET @sql = CASE
    WHEN @payment_duplicate_groups > 0 THEN
        'SELECT ''payment_info.order_no has duplicate values; unique index repair skipped'' message, ''FAIL'' status'
    WHEN @target_index_valid = 1 THEN
        'SELECT ''uk_payment_info_order_no already valid'' message, ''PASS'' status'
    WHEN @target_index_name_exists > 0 THEN
        'SELECT ''uk_payment_info_order_no exists with an unexpected definition'' message, ''FAIL'' status'
    WHEN @equivalent_unique_index IS NOT NULL THEN
        CONCAT(
            'ALTER TABLE payment_info RENAME INDEX `',
            REPLACE(@equivalent_unique_index, '`', '``'),
            '` TO `uk_payment_info_order_no`'
        )
    ELSE
        'ALTER TABLE payment_info ADD UNIQUE KEY uk_payment_info_order_no(order_no)'
END;
PREPARE repair_stmt FROM @sql;
EXECUTE repair_stmt;
DEALLOCATE PREPARE repair_stmt;

-- Remove the legacy non-unique index only after the target unique index is valid.
SET @target_index_valid = (
    SELECT COUNT(*)
    FROM (
        SELECT index_name,
               non_unique,
               GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_list
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'payment_info'
          AND index_name = 'uk_payment_info_order_no'
        GROUP BY index_name, non_unique
    ) indexes_found
    WHERE non_unique = 0
      AND columns_list = 'order_no'
);

SET @legacy_index_exists = (
    SELECT COUNT(*)
    FROM information_schema.statistics
    WHERE table_schema = DATABASE()
      AND table_name = 'payment_info'
      AND index_name = 'uniq_order_no'
      AND non_unique = 1
);

SET @sql = IF(
    @target_index_valid = 1 AND @legacy_index_exists > 0,
    'ALTER TABLE payment_info DROP INDEX uniq_order_no',
    'SELECT ''legacy payment index removal not required'' message, ''PASS'' status'
);
PREPARE repair_stmt FROM @sql;
EXECUTE repair_stmt;
DEALLOCATE PREPARE repair_stmt;

-- Post-repair verification.
SELECT column_type, is_nullable, column_default
FROM information_schema.columns
WHERE table_schema = DATABASE()
  AND table_name = 'product_sku'
  AND column_name = 'stock_num';

SELECT COUNT(*) AS invalid_stock_count
FROM product_sku
WHERE stock_num IS NULL OR stock_num < 0;

SELECT index_name,
       non_unique,
       GROUP_CONCAT(column_name ORDER BY seq_in_index SEPARATOR ',') AS columns_list
FROM information_schema.statistics
WHERE table_schema = DATABASE()
  AND table_name = 'payment_info'
GROUP BY index_name, non_unique
ORDER BY index_name;
