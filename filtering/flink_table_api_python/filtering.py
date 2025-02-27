from pyflink.table.confluent import ConfluentSettings, ConfluentTools
from pyflink.table import TableEnvironment, Row
from pyflink.table.expressions import col, row

def filter_example():
    settings = ConfluentSettings.from_file("./cloud.properties")
    env = TableEnvironment.create(settings)

    table_result = env.from_path("examples.marketplace.orders") \
        .select(col("customer_id"), col("product_id"), col("price")) \
        .filter(col("price") >= 50) \
        .execute()

    ConfluentTools.print_materialized_limit(table_result, 5)

    with table_result.collect() as rows:
        i = 0
        for row in rows:
            print(row[2])
            i += 1
            if i >= 5: break


if __name__ == '__main__':
    filter_example()
