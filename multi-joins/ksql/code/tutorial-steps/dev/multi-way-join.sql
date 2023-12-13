CREATE STREAM orders_enriched AS 
  SELECT customers.customerid AS customerid, customers.customername AS customername, 
         orders.orderid, orders.purchasedate,
         items.itemid, items.itemname
  FROM orders 
  LEFT JOIN customers on orders.customerid = customers.customerid 
  LEFT JOIN items on orders.itemid = items.itemid;
