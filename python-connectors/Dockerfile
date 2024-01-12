FROM cnfldemos/cp-server-connect-datagen:0.6.0-7.3.0

ENV CONNECT_PLUGIN_PATH="/usr/share/java,/usr/share/confluent-hub-components,/etc/kafka-connect/jars/"

USER root

RUN echo "===> Creating /app dir ..." \
     && mkdir -p /app \
     && chmod -R ag+w /app

RUN microdnf install gcc-c++ gcc findutils
RUN microdnf install which
ENV JAVA_HOME=/usr/lib/jvm/zulu11

RUN python3.9 -m venv /tmp/venv39
RUN source /tmp/venv39/bin/activate
RUN dnf install -y python39-devel
RUN pip3 install pemja

USER appuser

ADD https://confluent-csid-maven.s3.us-west-2.amazonaws.com/releases/io/confluent/pytools/confluent-pytools/0.1/confluent-pytools-0.1-jar-with-dependencies.jar /etc/kafka-connect/jars/
COPY code/*.py /app/
COPY code/requirements.txt /app/
COPY json/*.json /app/
