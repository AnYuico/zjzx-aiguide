-- One-time repair for products created before SKU status synchronization was added.
-- Review the SELECT result before executing the UPDATE in each environment.

select sku.id,
       sku.product_id,
       sku.status as sku_status,
       p.status as product_status
from product_sku sku
join product p on p.id = sku.product_id
where sku.is_deleted = 0
  and p.is_deleted = 0
  and sku.status <> p.status;

update product_sku sku
join product p on p.id = sku.product_id
set sku.status = p.status,
    sku.update_time = now()
where sku.is_deleted = 0
  and p.is_deleted = 0
  and sku.status <> p.status;
