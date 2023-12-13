INSERT INTO customers (id, first_name, last_name, email) VALUES (375, 'Janice', 'Smith', 'jsmith@mycompany.com');
INSERT INTO customers (id, first_name, last_name, email) VALUES (983, 'George', 'Mall', 'gmall@mycompany.com');

-- Wait 10 seconds before inserting the records below

INSERT INTO sales_orders (order_id, customer_id, item, order_total_usd) VALUES (44697328, 375, 'book', 29.99);
INSERT INTO sales_orders (order_id, customer_id, item, order_total_usd) VALUES (44697329, 375, 'guitar', 215.99);
INSERT INTO sales_orders (order_id, customer_id, item, order_total_usd) VALUES (44697330, 983, 'thermometer', 12.99);
INSERT INTO sales_orders (order_id, customer_id, item, order_total_usd) VALUES (44697331, 983, 'scarf', 32.99);
INSERT INTO sales_orders (order_id, customer_id, item, order_total_usd) VALUES (44697332, 375, 'doormat', 15.99);
INSERT INTO sales_orders (order_id, customer_id, item, order_total_usd) VALUES (44697333, 983, 'clippers', 65.99);
