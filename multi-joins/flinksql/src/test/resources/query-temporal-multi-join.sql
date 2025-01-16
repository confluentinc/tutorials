SELECT
    orders.order_id AS order_id,
    customers.name AS customer_name,
    products.name AS product_name
FROM orders
    LEFT JOIN customers FOR SYSTEM_TIME AS OF orders.ts
       ON orders.customer_id = customers.customer_id
    LEFT JOIN products FOR SYSTEM_TIME AS OF orders.ts
       ON orders.item_id = products.product_id;
