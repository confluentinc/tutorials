INSERT INTO customers (customer_id, first_name, last_name, email, gender, income, fico) VALUES  (1,'Waylen','Tubble','wtubble0@hc360.com','Male',403646, 465);
INSERT INTO customers (customer_id, first_name, last_name, email, gender, income, fico) VALUES  (2,'Joell','Wilshin','jwilshin1@yellowpages.com','Female',109825, 705);
INSERT INTO customers (customer_id, first_name, last_name, email, gender, income, fico) VALUES  (3,'Ilaire','Latus','ilatus2@baidu.com','Male',407964, 750);

INSERT INTO offers (offer_id, offer_name, offer_url) VALUES (1,'new_savings','http://google.com.br/magnis/dis/parturient.json');
INSERT INTO offers (offer_id, offer_name, offer_url) VALUES (2,'new_checking','https://earthlink.net/in/ante.js');
INSERT INTO offers (offer_id, offer_name, offer_url) VALUES (3,'new_home_loan','https://webs.com/in/ante.jpg');
INSERT INTO offers (offer_id, offer_name, offer_url) VALUES (4,'new_auto_loan','http://squidoo.com/venenatis/non/sodales/sed/tincidunt/eu.js');
INSERT INTO offers (offer_id, offer_name, offer_url) VALUES (5,'no_offer','https://ezinearticles.com/ipsum/primis/in/faucibus/orci/luctus.html');

INSERT INTO customer_activity_stream (customer_id, activity_id, ip_address, activity_type, propensity_to_buy) VALUES (1, 1,'121.219.110.170','branch_visit',0.4);
INSERT INTO customer_activity_stream (customer_id, activity_id, ip_address, activity_type, propensity_to_buy) VALUES (2, 2,'210.232.55.188','deposit',0.56);
INSERT INTO customer_activity_stream (customer_id, activity_id, ip_address, activity_type, propensity_to_buy) VALUES (3, 3,'84.197.123.173','web_open',0.33);
INSERT INTO customer_activity_stream (customer_id, activity_id, ip_address, activity_type, propensity_to_buy) VALUES (1, 4,'70.149.233.32','deposit',0.41);
INSERT INTO customer_activity_stream (customer_id, activity_id, ip_address, activity_type, propensity_to_buy) VALUES (2, 5,'221.234.209.67','deposit',0.44);
INSERT INTO customer_activity_stream (customer_id, activity_id, ip_address, activity_type, propensity_to_buy) VALUES (3, 6,'102.187.28.148','web_open',0.33);
INSERT INTO customer_activity_stream (customer_id, activity_id, ip_address, activity_type, propensity_to_buy) VALUES (1, 7,'135.37.250.250','mobile_open',0.97);
INSERT INTO customer_activity_stream (customer_id, activity_id, ip_address, activity_type, propensity_to_buy) VALUES (2, 8,'122.157.243.25','deposit',0.83);
INSERT INTO customer_activity_stream (customer_id, activity_id, ip_address, activity_type, propensity_to_buy) VALUES (3, 9,'114.215.212.181','deposit',0.86);
INSERT INTO customer_activity_stream (customer_id, activity_id, ip_address, activity_type, propensity_to_buy) VALUES (1, 10,'248.248.0.78','new_account',0.14);
