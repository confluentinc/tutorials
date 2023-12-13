-- For the purposes of this recipe when testing by inserting records manually,
--  a short pause between these insert groups is required. This allows
--  the merchant location data to be processed by the merchants_by_geohash
--  table before the user location data is joined in the alerts_raw stream.
INSERT INTO MERCHANT_LOCATIONS (id, latitude, longitude, description, geohash) VALUES (1, 14.5486606, 121.0477211, '7-Eleven RCBC Center', 'wdw4f88206fx');
INSERT INTO MERCHANT_LOCATIONS (id, latitude, longitude, description, geohash) VALUES (2, 14.5473328, 121.0516176, 'Jordan Manila', 'wdw4f87075kt');
INSERT INTO MERCHANT_LOCATIONS (id, latitude, longitude, description, geohash) VALUES (3, 14.5529666, 121.0516716, 'Lawson Eco Tower', 'wdw4f971hmsv');

-- Wait 10 seconds before inserting the records below

INSERT INTO USER_LOCATIONS (id, latitude, longitude, geohash) VALUES (1, 14.5472791, 121.0475401, 'wdw4f820h17g');
INSERT INTO USER_LOCATIONS (id, latitude, longitude, geohash) VALUES (2, 14.5486952, 121.0521851, 'wdw4f8e82376');
INSERT INTO USER_LOCATIONS (id, latitude, longitude, geohash) VALUES (2, 14.5517401, 121.0518652, 'wdw4f9560buw');
INSERT INTO USER_LOCATIONS (id, latitude, longitude, geohash) VALUES (2, 14.5500341, 121.0555802, 'wdw4f8vbp6yv');
