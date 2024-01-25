# Transform nested JSON 

Consider a topic containing product orders. Each order contains data about the customer and the product, specified as nested data. In this tutorial, we'll write a program that transforms each order into a new version that contains all the data as flat fields.

## Setup

You have JSON data in a topic that has the following structure:
```json
{
  "id": "1",
  "timestamp": "2020-01-18 01:12:05",
  "amount": 84.02,
  "customer": {
    "firstName": "Roberto",
    "lastName": "Smithe",
    "phoneNumber": "1234567899",
    "address": {
      "street": "Street SDF",
      "number": "8602",
      "zipcode": "27640",
      "city": "Raleigh",
      "state": "NC"
    }
  },
  "product": {
    "sku": "P12345",
    "name": "Highly Durable Glue",
    "vendor": {
      "vendorName": "Acme Corp",
      "country": "US"
    }
  }
}
```
The first step to working with this nested JSON is to create a stream over the topic and use the `STRUCT` keyword to define the fields that contain nested data:
```sql
CREATE STREAM ORDERS (
    id VARCHAR,
    timestamp VARCHAR,
    amount DOUBLE,
    customer STRUCT<firstName VARCHAR,
                    lastName VARCHAR,
                    phoneNumber VARCHAR,
                    address STRUCT<street VARCHAR,
                                   number VARCHAR,
                                   zipcode VARCHAR,
                                   city VARCHAR,
                                   state VARCHAR>>,
    product STRUCT<sku VARCHAR,
                   name VARCHAR,
                   vendor STRUCT<vendorName VARCHAR,
                                 country VARCHAR>>)
    WITH (KAFKA_TOPIC = 'ORDERS',
          VALUE_FORMAT = 'JSON',
          TIMESTAMP = 'TIMESTAMP',
          TIMESTAMP_FORMAT = 'yyyy-MM-dd HH:mm:ss',
          PARTITIONS = 1);
```
     
Next, create a stream that will extract the nested fields into a flat structure:
```sql
CREATE STREAM FLATTENED_ORDERS AS
    SELECT
        ID AS ORDER_ID,
        TIMESTAMP AS ORDER_TS,
        AMOUNT AS ORDER_AMOUNT,
        CUSTOMER->FIRSTNAME AS CUST_FIRST_NAME,
        CUSTOMER->LASTNAME AS CUST_LAST_NAME,
        CUSTOMER->PHONENUMBER AS CUST_PHONE_NUMBER,
        CUSTOMER->ADDRESS->STREET AS CUST_ADDR_STREET,
        CUSTOMER->ADDRESS->NUMBER AS CUST_ADDR_NUMBER,
        CUSTOMER->ADDRESS->ZIPCODE AS CUST_ADDR_ZIPCODE,
        CUSTOMER->ADDRESS->CITY AS CUST_ADDR_CITY,
        CUSTOMER->ADDRESS->STATE AS CUST_ADDR_STATE,
        PRODUCT->SKU AS PROD_SKU,
        PRODUCT->NAME AS PROD_NAME,
        PRODUCT->VENDOR->VENDORNAME AS PROD_VENDOR_NAME,
        PRODUCT->VENDOR->COUNTRY AS PROD_VENDOR_COUNTRY
    FROM
        ORDERS;
```
Notice the pattern of `STRUCT->STRUCT->FIELD` to drill down to the nested fields.
 
Now when you want to run query selecting certain attributes of an order you can use much simpler queries:
```sql
SELECT
    ORDER_ID,
    ORDER_TS,
    ORDER_AMOUNT,
    CUST_FIRST_NAME,
    CUST_LAST_NAME,
    CUST_PHONE_NUMBER,
    CUST_ADDR_STREET,
    PROD_SKU,
    PROD_NAME,
    PROD_VENDOR_NAME,
    PROD_VENDOR_COUNTRY
FROM FLATTENED_ORDERS
EMIT CHANGES;
```
