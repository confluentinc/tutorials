#!/bin/bash
# Makes the Tableflow REST Catalog the default catalog for this container's Spark Thrift server,
# so that other apps (like the Part 3 dashboard) can query Tableflow tables over JDBC/Thrift.
# The Jupyter notebook configures its own session and doesn't depend on this.
if [[ -n "$TABLEFLOW_REST_URI" ]]; then
  cat >> /opt/spark/conf/spark-defaults.conf <<EOF
spark.sql.catalog.tableflowdemo                                  org.apache.iceberg.spark.SparkCatalog
spark.sql.catalog.tableflowdemo.type                             rest
spark.sql.catalog.tableflowdemo.uri                              $TABLEFLOW_REST_URI
spark.sql.catalog.tableflowdemo.credential                       $TABLEFLOW_API_KEY:$TABLEFLOW_API_SECRET
spark.sql.catalog.tableflowdemo.io-impl                          org.apache.iceberg.aws.s3.S3FileIO
spark.sql.catalog.tableflowdemo.rest-metrics-reporting-enabled   false
spark.sql.catalog.tableflowdemo.s3.remote-signing-enabled        true
spark.sql.defaultCatalog                                         tableflowdemo
EOF
fi
exec ./entrypoint.sh "$@"
