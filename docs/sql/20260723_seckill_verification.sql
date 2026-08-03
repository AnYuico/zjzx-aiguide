-- Run after a seckill load/failure-recovery test.
-- Every query should return PASS, 0 rows, or a non-negative stock value.

SELECT 'activity_stock_non_negative' AS check_name,
       IF(COUNT(*) = 0, 'PASS', 'FAIL') AS result
FROM seckill_sku
WHERE available_stock < 0
   OR available_stock > total_stock;

SELECT 'physical_stock_non_negative' AS check_name,
       IF(COUNT(*) = 0, 'PASS', 'FAIL') AS result
FROM product_sku
WHERE stock_num < 0;

SELECT 'one_user_one_order' AS check_name,
       IF(COUNT(*) = 0, 'PASS', 'FAIL') AS result
FROM (
    SELECT activity_id, user_id, sku_id
    FROM seckill_order_request
    WHERE is_deleted = 0
    GROUP BY activity_id, user_id, sku_id
    HAVING COUNT(*) > 1
) duplicate_user_order;

SELECT 'request_idempotency' AS check_name,
       IF(COUNT(*) = 0, 'PASS', 'FAIL') AS result
FROM (
    SELECT request_id
    FROM seckill_order_request
    WHERE is_deleted = 0
    GROUP BY request_id
    HAVING COUNT(*) > 1
) duplicate_request;

SELECT 'successful_request_has_order' AS check_name,
       IF(COUNT(*) = 0, 'PASS', 'FAIL') AS result
FROM seckill_order_request r
LEFT JOIN order_info o
       ON o.id = r.order_id
      AND o.order_no = r.order_no
WHERE r.status = 2
  AND (r.order_id IS NULL OR o.id IS NULL);

SELECT s.activity_id, s.sku_id, s.total_stock, s.available_stock,
       SUM(CASE WHEN r.status = 2 THEN 1 ELSE 0 END) AS successful_orders,
       SUM(CASE WHEN r.status = 4 THEN 1 ELSE 0 END) AS cancelled_orders
FROM seckill_sku s
LEFT JOIN seckill_order_request r
       ON r.seckill_sku_id = s.id
      AND r.is_deleted = 0
GROUP BY s.id, s.activity_id, s.sku_id, s.total_stock, s.available_stock
ORDER BY s.activity_id, s.sku_id;
