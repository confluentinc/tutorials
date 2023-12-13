INSERT INTO fd_cust_raw_stream (id, first_name, last_name, email, avg_credit_spend) VALUES (6011000990139424, 'Janice', 'Smith', 'jsmith@mycompany.com', 500.00);
INSERT INTO fd_cust_raw_stream (id, first_name, last_name, email, avg_credit_spend) VALUES (3530111333300000, 'George', 'Mall', 'gmall@mycompany.com', 20.00);

INSERT INTO fd_transactions (account_id, timestamp, card_type, amount, ip_address, transaction_id) VALUES (6011000990139424, '2021-09-23T10:50:00.000Z', 'visa', 542.99, '192.168.44.1', '3985757');
INSERT INTO fd_transactions (account_id, timestamp, card_type, amount, ip_address, transaction_id) VALUES (6011000990139424, '2021-09-23T10:50:01.000Z', 'visa', 611.48, '192.168.44.1', '8028435');
INSERT INTO fd_transactions (account_id, timestamp, card_type, amount, ip_address, transaction_id) VALUES (3530111333300000, '2021-09-23T10:50:00.000Z', 'mastercard', 10.31, '192.168.101.3', '1695780');
INSERT INTO fd_transactions (account_id, timestamp, card_type, amount, ip_address, transaction_id) VALUES (3530111333300000, '2021-09-23T10:50:00.000Z', 'mastercard', 5.37, '192.168.101.3', '1695780');
