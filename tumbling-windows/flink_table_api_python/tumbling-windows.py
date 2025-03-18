from pyflink.table import TableEnvironment
from pyflink.table.confluent import ConfluentSettings, ConfluentTools
from pyflink.table.expressions import col, lit
from pyflink.table.window import Tumble


def tumbling_windows_example():
    settings = ConfluentSettings.from_file('./cloud.properties')
    env = TableEnvironment.create(settings)

    env.use_catalog('examples')
    env.use_database('marketplace')

    table_result = env.from_path('examples.marketplace.orders') \
        .window(
            Tumble.over(lit(2).seconds)
                .on(col('$rowtime'))
                .alias('window')
        ) \
        .group_by(col('window')) \
        .select(
            col('customer_id').count.alias('count'),
            col('window').start.alias('window_start'),
            col('window').end.alias('window_end')
        ) \
        .execute()

    ConfluentTools.print_materialized_limit(table_result, 2)

    with table_result.collect() as rows:
        i = 0
        for row in rows:
            print(f"count: {row[0]}, window start: {row[1]}, window end: {row[2]}")
            i += 1
            if i >= 2: break


if __name__ == '__main__':
    tumbling_windows_example()
