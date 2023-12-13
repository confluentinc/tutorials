package io.confluent.developer.produce

import java.util.concurrent.Future

import io.confluent.developer.Configuration
import io.confluent.developer.Configuration.ProducerConf
import io.confluent.developer.schema.ScalaReflectionSerde.reflectionSerializer4S
import io.confluent.developer.schema.{Book, ScalaReflectionSerde}
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
import org.apache.kafka.clients.producer.{Callback, KafkaProducer, ProducerRecord, RecordMetadata}
import org.apache.kafka.common.serialization.{Serdes, Serializer}
import org.apache.kafka.common.utils.Bytes
import org.slf4j.{Logger, LoggerFactory}
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import kantan.csv._
import kantan.csv.ops._

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._

object Producer extends App with ScalaReflectionSerde with Configuration {

  private def logger: Logger = LoggerFactory.getLogger(getClass)

  private val producerConf = ConfigSource.default.at("producer").loadOrThrow[ProducerConf]

  private val schemaRegistryConfigMap: Map[String, AnyRef] = Map[String, AnyRef](
    SCHEMA_REGISTRY_URL_CONFIG -> producerConf.clientConfig.getString(SCHEMA_REGISTRY_URL_CONFIG)
  )

  def produce(producer: KafkaProducer[Bytes, Book], topic: String, book: Book): Future[RecordMetadata] = {
    val record: ProducerRecord[Bytes, Book] = new ProducerRecord(topic, book)

    producer.send(record, new Callback {
      override def onCompletion(metadata: RecordMetadata, exception: Exception): Unit = Option(exception)
        .map(ex => logger error s"fail to produce record due to: ${ex.getMessage}")
        .getOrElse(logger info s"successfully produced - ${printMetaData(metadata)}")
    })
  }

  def parseCsvLine(line: String) = line.readCsv[Vector, Book](rfc.withoutHeader.withCellSeparator(',').withQuote('\''))

  def printMetaData(metadata: RecordMetadata) =
    s"""topic: ${metadata.topic()},
       | partition: ${metadata.partition()},
       | offset: ${metadata.offset()}
       | (ts: ${metadata.timestamp()})""".stripMargin.replace("\n", "")

  logger debug "creating the serializer and configuration"
  private val bookSerializer: Serializer[Book] = reflectionSerializer4S[Book]
  bookSerializer.configure(schemaRegistryConfigMap.asJava, false)

  logger debug "creating the kafka producer"
  private val producer = new KafkaProducer[Bytes, Book](
    producerConf.clientConfig.toProperties,
    Serdes.Bytes().serializer(),
    bookSerializer
  )

  if (args.isEmpty) {
    var input = ""
    while (input != "exit") {
      Thread.sleep((2 second) toMillis)
      System.out.print("produce-a-book> ")
      input = scala.io.StdIn.readLine()
      if (input != "exit") {
        val books: Vector[ReadResult[Book]] = parseCsvLine(input)
        books.foreach { maybeBook =>
          maybeBook
            .map(book => produce(producer, producerConf.topics.bookTopic.name, book))
            .left.foreach(error => logger warn error.getMessage)
        }
      }
    }
  } else {
    parseCsvLine(args.mkString("\n")).foreach { maybeBook =>
      maybeBook
        .map(book => produce(producer, producerConf.topics.bookTopic.name, book))
        .left.foreach(error => logger warn error.getMessage)
    }
  }

  logger info "closing the book producer application"
  producer.flush()
  producer.close()
}
