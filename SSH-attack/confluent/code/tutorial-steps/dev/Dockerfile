FROM confluentinc/cp-server-connect-base:7.3.0

ENV CONNECT_PLUGIN_PATH="/usr/share/java,/usr/share/confluent-hub-components"

RUN confluent-hub install --no-prompt confluentinc/kafka-connect-syslog:1.3.4
