from pyflink.table import TableEnvironment
from pyflink.table.confluent import ConfluentSettings, ConfluentTools
from pyflink.table.expressions import col, lit
from pyflink.table.window import Slide


def join_example():
    settings = ConfluentSettings.from_file('./cloud.properties')
    env = TableEnvironment.create(settings)

    env.use_catalog('examples')
    env.use_database('marketplace')

    orders_table = env.from_path('examples.marketplace.orders') \
        .select(
            col('order_id'),
            col('customer_id').alias('order_customer_id'),
            col('product_id'),
            col('price'),
            col('$rowtime').alias('order_time')
        )
    customers_table = env.from_path('examples.marketplace.customers') \
        .select(
            col('customer_id'),
            col('name'),
            col('$rowtime').alias('customer_time')
        )

    table_result = orders_table \
        .join(customers_table, col('order_time') >= col('customer_time')
                               and col('order_customer_id') == col('customer_id')) \
        .select(
            col('order_id'),
            col('product_id'),
            col('name'),
            col('order_time'),
            col('customer_time')
        ) \
        .execute()

    ConfluentTools.print_materialized_limit(table_result, 5)


if __name__ == '__main__':
    join_example()
