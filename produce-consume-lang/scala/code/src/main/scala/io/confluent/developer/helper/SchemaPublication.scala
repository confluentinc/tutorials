package io.confluent.developer.helper

import java.io.IOException

import io.confluent.developer.{Configuration, schema}
import io.confluent.developer.Configuration.HelperConf
import io.confluent.developer.schema.{Book, BookAvroSchema}
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
import org.slf4j.{Logger, LoggerFactory}
import pureconfig.ConfigSource
import pureconfig.generic.auto._

import scala.annotation.tailrec
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object SchemaPublication extends App with Configuration {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  @tailrec
  def retryCallSchemaRegistry(logger: Logger)(countdown: Int, interval: Duration, f: => Unit): Try[Unit] = {
    Try(f) match {
      case result@Success(_) =>
        logger info "Successfully call the Schema Registry."
        result
      case result@Failure(_) if countdown <= 0 =>
        logger error "Fail to call the Schema Registry for the last time."
        result
      case Failure(_) if countdown > 0 =>
        logger error s"Fail to call the Schema Registry, retry in ${interval.toSeconds} secs."
        Thread.sleep(interval.toMillis)
        retryCallSchemaRegistry(logger)(countdown - 1, interval, f)
    }
  }

  val helperConf = ConfigSource.default.at("helper").loadOrThrow[HelperConf]

  val schemaRegistryClient = new CachedSchemaRegistryClient(
    helperConf.clientConfig.getString(SCHEMA_REGISTRY_URL_CONFIG),
    200
  )

  retryCallSchemaRegistry(logger)(
    helperConf.schemaRegistryRetriesNum,
    helperConf.schemaRegistryRetriesInterval, {
      schemaRegistryClient.register(s"${helperConf.topics.bookTopic.name}-value", new AvroSchema(BookAvroSchema))
    }
  ) match {
    case failure@Failure(_: IOException | _: RestClientException) =>
      failure.exception.printStackTrace()
    case _ =>
      logger.info(s"Schemas publication at: ${helperConf.clientConfig.getString(SCHEMA_REGISTRY_URL_CONFIG)}")
  }
}
