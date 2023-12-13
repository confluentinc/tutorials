-- tenant_id is in the form of a resource name used to indicate the 
--  data center provider, country, regional locale, and tenant id
INSERT INTO tenant_occupancy (tenant_id, customer_id) VALUES ('dc:eqix:us:chi1:12', 924);
INSERT INTO tenant_occupancy (tenant_id, customer_id) VALUES ('dc:eqix:us:chi1:10', 243);
INSERT INTO tenant_occupancy (tenant_id, customer_id) VALUES ('dc:kddi:eu:ber1:15', 924);
INSERT INTO tenant_occupancy (tenant_id, customer_id) VALUES ('dc:kddi:eu:ber1:20', 123);
INSERT INTO tenant_occupancy (tenant_id, customer_id) VALUES ('dc:kddi:cn:hnk2:11', 243);

-- power readings contain two distinct readings. The current total utilization of the
--  panel, and the monthly total wattage usage for the referenced tenant
INSERT INTO panel_power_readings (panel_id, tenant_id, panel_current_utilization, tenant_kwh_usage) VALUES (1, 'dc:eqix:us:chi1:12', 1.05, 1034);
INSERT INTO panel_power_readings (panel_id, tenant_id, panel_current_utilization, tenant_kwh_usage) VALUES (2, 'dc:eqix:us:chi1:10', 0.85, 867);
INSERT INTO panel_power_readings (panel_id, tenant_id, panel_current_utilization, tenant_kwh_usage) VALUES (1, 'dc:kddi:eu:ber1:15', 0.54, 345);
INSERT INTO panel_power_readings (panel_id, tenant_id, panel_current_utilization, tenant_kwh_usage) VALUES (2, 'dc:kddi:eu:ber1:20', 0.67, 288);
INSERT INTO panel_power_readings (panel_id, tenant_id, panel_current_utilization, tenant_kwh_usage) VALUES (1, 'dc:kddi:cn:hnk2:11', 1.11, 1119);
INSERT INTO panel_power_readings (panel_id, tenant_id, panel_current_utilization, tenant_kwh_usage) VALUES (1, 'dc:eqix:us:chi1:12', 1.01, 1134);
INSERT INTO panel_power_readings (panel_id, tenant_id, panel_current_utilization, tenant_kwh_usage) VALUES (2, 'dc:eqix:us:chi1:10', 0.75, 898);
INSERT INTO panel_power_readings (panel_id, tenant_id, panel_current_utilization, tenant_kwh_usage) VALUES (1, 'dc:kddi:cn:hnk2:11', 1.10, 1201);
