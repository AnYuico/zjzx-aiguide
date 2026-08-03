-- Read-only verification for JMeter order submission and seckill tests.
-- Set the activity ID after creating the dedicated seckill activity.

SET @ordinary_sku_id = 14;
SET @seckill_sku_id = 15;
SET @seckill_activity_id = 0;

SELECT id, sku_name, stock_num, sale_num, status, is_deleted
FROM product_sku
WHERE id IN (@ordinary_sku_id, @seckill_sku_id);

SELECT COUNT(*) AS jmeter_order_count,
       SUM(order_status = 0) AS waiting_payment_count,
       SUM(order_status = -1) AS cancelled_count,
       MIN(create_time) AS first_created_at,
       MAX(create_time) AS last_created_at
FROM order_info
WHERE remark = 'jmeter-load-test'
  AND is_deleted = 0;

SELECT order_status, COUNT(*) AS order_count
FROM order_info
WHERE remark = 'jmeter-load-test'
  AND is_deleted = 0
GROUP BY order_status
ORDER BY order_status;

SELECT status, COUNT(*) AS request_count
FROM order_submit_request
WHERE request_id IN (
    SELECT request_id
    FROM order_info
    WHERE remark = 'jmeter-load-test'
)
GROUP BY status
ORDER BY status;

SELECT r.status, COUNT(*) AS reservation_count, SUM(r.sku_num) AS sku_units
FROM inventory_reservation r
JOIN order_info o ON o.order_no = r.order_no
WHERE o.remark = 'jmeter-load-test'
GROUP BY r.status
ORDER BY r.status;

SELECT event_type, status, COUNT(*) AS event_count,
       SUM(retry_count) AS retry_count
FROM mq_outbox
WHERE payload LIKE '%jmeter-load-test%'
   OR event_id IN (
       SELECT CONCAT('order.timeout:', order_no)
       FROM order_info
       WHERE remark = 'jmeter-load-test'
   )
GROUP BY event_type, status
ORDER BY event_type, status;

SELECT COUNT(*) AS negative_stock_count
FROM product_sku
WHERE stock_num < 0;

SELECT id, activity_id, sku_id, total_stock, available_stock, status
FROM seckill_sku
WHERE activity_id = @seckill_activity_id
  AND sku_id = @seckill_sku_id;

SELECT status, COUNT(*) AS request_count,
       SUM(stock_returned) AS returned_count
FROM seckill_order_request
WHERE activity_id = @seckill_activity_id
  AND sku_id = @seckill_sku_id
GROUP BY status
ORDER BY status;

SELECT COUNT(*) AS seckill_order_count
FROM order_info o
JOIN seckill_order_request r ON r.order_id = o.id
WHERE r.activity_id = @seckill_activity_id
  AND r.sku_id = @seckill_sku_id
  AND r.status = 2
  AND o.is_deleted = 0;
