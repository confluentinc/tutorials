package io.confluent.developer;

import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsContext;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.apache.kafka.common.requests.RequestContext;
import org.apache.kafka.common.utils.Utils;
import org.apache.kafka.server.authorizer.AuthorizableRequestContext;
import org.apache.kafka.server.telemetry.ClientTelemetry;
import org.apache.kafka.server.telemetry.ClientTelemetryPayload;
import org.apache.kafka.server.telemetry.ClientTelemetryReceiver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import io.grpc.ConnectivityState;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceResponse;
import io.opentelemetry.proto.collector.metrics.v1.MetricsServiceGrpc;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.MetricsData;
import io.opentelemetry.proto.metrics.v1.ResourceMetrics;
import io.opentelemetry.proto.resource.v1.Resource;

/**
 * Client MetricsReporter that aggregates OpenTelemetry Protocol(OTLP) metrics received from client,
 * enhances them with additional client labels and forwards them via gRPC Client to an external OTLP
 * receiver.
 */
public class ClientOtlpMetricsReporter implements MetricsReporter, ClientTelemetry {

  private static final Logger log = LoggerFactory.getLogger(ClientOtlpMetricsReporter.class);

  // Environment variable to set the gRPC endpoint.
  private static final String GRPC_ENDPOINT_CONFIG = "OTEL_EXPORTER_OTLP_ENDPOINT";
  private static final int DEFAULT_GRPC_PORT = 4317;
  // Kafka-specific labels.
  private static final String KAFKA_BROKER_ID = "kafka.broker.id";
  private static final String KAFKA_CLUSTER_ID = "kafka.cluster.id";
  // Client-specific labels.
  public static final String CLIENT_ID = "client_id";
  public static final String CLIENT_INSTANCE_ID = "client_instance_id";
  public static final String CLIENT_SOFTWARE_NAME = "client_software_name";
  public static final String CLIENT_SOFTWARE_VERSION = "client_software_version";
  public static final String CLIENT_SOURCE_ADDRESS = "client_source_address";
  public static final String CLIENT_SOURCE_PORT = "client_source_port";
  public static final String PRINCIPAL = "principal";

  private MetricsGrpcClient grpcService;
  private Map<String, String> metricsContext;

  /**
   * Initializes the MetricsReporter with a {@link MetricsGrpcClient} at an endpoint defined by the
   * {@value GRPC_ENDPOINT_CONFIG} environment variable
   */
  public ClientOtlpMetricsReporter() {
    String grpcEndpoint = System.getenv(GRPC_ENDPOINT_CONFIG);

    if (Utils.isBlank(grpcEndpoint)) {
      log.info("environment variable: {} is not set. Using localhost and default grpc port: {}",
          GRPC_ENDPOINT_CONFIG, DEFAULT_GRPC_PORT);
      try {
        grpcEndpoint = InetAddress.getLocalHost().getHostAddress() + ":" + DEFAULT_GRPC_PORT;
      } catch (UnknownHostException e) {
        log.error("Failed to get local host address: {}", e.getMessage());
        return;
      }
    }

    ManagedChannel grpcChannel = ManagedChannelBuilder.forTarget(grpcEndpoint).usePlaintext().build();
    grpcService = new MetricsGrpcClient(grpcChannel);
  }

  @Override
  public void configure(Map<String, ?> configs) {
    // No configuration needed
  }

  @Override
  public void contextChange(MetricsContext metricsContext) {
    this.metricsContext = metricsContext.contextLabels();
  }

  @Override
  public void close() {
    grpcService.close();
  }

  @Override
  public ClientTelemetryReceiver clientReceiver() {
    return (context, payload) -> {
      try {
        log.debug("Exporting metrics. Context: {}, payload: {}", context, payload);
        if (payload == null || payload.data() == null) {
          log.warn("exportMetrics - Client did not include payload, skipping export");
          return;
        }

        log.debug("Metrics data compressed size: {}", payload.data().remaining());
        MetricsData metricsData = MetricsData.parseFrom(payload.data());
        if (metricsData == null || metricsData.getResourceMetricsCount() == 0) {
          log.warn("No metrics available to export, skipping export");
          return;
        }

        log.debug("Pre-processed metrics data: {}, count: {}, size: {}",
            metricsData, metricsData.getResourceMetricsCount(), metricsData.getSerializedSize());
        List<ResourceMetrics> metrics = metricsData.getResourceMetricsList();
        // Enhance metrics with labels from request context, payload and broker, if any.
        Map<String, String> labels = fetchLabels(context, payload);
        // Update labels to metrics.
        metrics = appendLabelsToResource(metrics, labels);

        log.debug("Exporting metrics: {}, count: {}, size: {}", metrics, metricsData.getResourceMetricsCount(),
            metricsData.getSerializedSize());
        grpcService.export(metrics);
      } catch (Exception e) {
        log.error("Error processing client telemetry metrics: ", e);
      }
    };
  }

  private Map<String, String> fetchLabels(AuthorizableRequestContext context, ClientTelemetryPayload payload) {
    RequestContext requestContext = (RequestContext) context;
    Map<String, String> labels = new HashMap<>();

    putIfNotNull(labels, CLIENT_ID, context.clientId());
    putIfNotNull(labels, CLIENT_INSTANCE_ID, payload.clientInstanceId().toString());
    putIfNotNull(labels, CLIENT_SOFTWARE_NAME, requestContext.clientInformation.softwareName());
    putIfNotNull(labels, CLIENT_SOFTWARE_VERSION, requestContext.clientInformation.softwareVersion());
    putIfNotNull(labels, CLIENT_SOURCE_ADDRESS, requestContext.clientAddress().getHostAddress());
    putIfNotNull(labels, CLIENT_SOURCE_PORT, Integer.toString(requestContext.clientPort.orElse(-1)));
    putIfNotNull(labels, PRINCIPAL, requestContext.principal().getName());

    // Include Kafka cluster and broker id from the MetricsContext, if available.
    putIfNotNull(labels, KAFKA_CLUSTER_ID, metricsContext.get(KAFKA_CLUSTER_ID));
    putIfNotNull(labels, KAFKA_BROKER_ID, metricsContext.get(KAFKA_BROKER_ID));

    return labels;
  }

  private static void putIfNotNull(Map<String, String> map, String key, String value) {
    Optional.ofNullable(value).ifPresent(v -> map.put(key, v));
  }

  /**
   * Returns a list of ResourceMetrics where each metrics is enhanced by adding client information as
   * additional resource level attributes
   * @param resourceMetrics resource metrics sent by client
   * @param labels broker added labels containing client information
   * @return enhanced ResourceMetrics list
   */
  private List<ResourceMetrics> appendLabelsToResource(
      List<ResourceMetrics> resourceMetrics,
      Map<String, String> labels
  ) {
    List<ResourceMetrics> updatedResourceMetrics = new ArrayList<>();
    resourceMetrics.forEach(rm -> {
      Resource.Builder resource = rm.getResource().toBuilder();
      labels.forEach((k, v) -> resource.addAttributes(
          KeyValue.newBuilder()
              .setKey(k)
              .setValue(AnyValue.newBuilder().setStringValue(v))
              .build()
      ));
      ResourceMetrics updatedMetric = rm.toBuilder()
          .setResource(resource.build())
          .build();
      updatedResourceMetrics.add(updatedMetric);
    });

    return updatedResourceMetrics;
  }

  @Override
  public void init(List<KafkaMetric> metrics) {}

  @Override
  public void metricChange(KafkaMetric metric) {}

  @Override
  public void metricRemoval(KafkaMetric metric) {}

  private static final class MetricsGrpcClient implements AutoCloseable {

    private static final int GRPC_CHANNEL_TIMEOUT_SECS = 30;

    private final StreamObserver<ExportMetricsServiceResponse> streamObserver =
        new StreamObserver<ExportMetricsServiceResponse>() {

          @Override
          public void onNext(ExportMetricsServiceResponse value) {
            // Do nothing since response is blank.
          }

          @Override
          public void onError(Throwable t) {
            log.error("Unable to export metrics request to {}. gRPC Connectivity in: {}:",
                endpoint, getChannelState(), t.getCause());
          }

          @Override
          public void onCompleted() {
            log.info("Successfully exported metrics request to {}", endpoint);
          }
        };

    private final MetricsServiceGrpc.MetricsServiceStub grpcClient;
    private final String endpoint;
    private final ManagedChannel grpcChannel;

    /**
     * Starts a {@link MetricsServiceGrpc} at with a given gRPC {@link ManagedChannel}
     */
    MetricsGrpcClient(ManagedChannel channel) {
      grpcChannel = channel;
      grpcClient = MetricsServiceGrpc.newStub(channel);

      endpoint = grpcChannel.authority();
      log.info("Started gRPC Client for endpoint  {}", endpoint);
    }

    /**
     * Exports a ExportMetricsServiceRequest to an external endpoint
     * @param resourceMetrics metrics to export
     */
    private void export(List<ResourceMetrics> resourceMetrics) {
      ExportMetricsServiceRequest request = ExportMetricsServiceRequest.newBuilder()
          .addAllResourceMetrics(resourceMetrics)
          .build();

      grpcClient.export(request, streamObserver);
    }

    /**
     * Shuts down the gRPC {@link ManagedChannel}
     */
    @Override
    public void close()  {
      try {
        log.info("Shutting down gRPC channel at {}", endpoint);
        grpcChannel.shutdown().awaitTermination(GRPC_CHANNEL_TIMEOUT_SECS, TimeUnit.SECONDS);
      } catch (Exception e) {
        log.error("Failed Shutting down gRPC channel at {}", endpoint, e);
      }
    }

    private ConnectivityState getChannelState() {
      return grpcChannel.getState(true);
    }
  }
}