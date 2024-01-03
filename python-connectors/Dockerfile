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

# temporary link until the fatjar is on our artifactory
ADD https://www.dropbox.com/scl/fi/569vla0f8j04c6kr9twcn/confluent-pytools-0.1-jar-with-dependencies.jar?rlkey=ktm71zuatdnvm327p5d97i88w&dl=1 /etc/kafka-connect/jars/
COPY code/*.py /app/
COPY code/requirements.txt /app/
COPY json/*.json /app/
