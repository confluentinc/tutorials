package io.confluent.developer.consume

import java.util.concurrent.{Executors, ScheduledExecutorService, TimeUnit}

import io.confluent.developer.Configuration
import io.confluent.developer.Configuration.ConsumerConf
import io.confluent.developer.schema.ScalaReflectionSerde.reflectionDeserializer4S
import io.confluent.developer.schema.{Book, ScalaReflectionSerde}
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
import org.apache.kafka.clients.consumer.{ConsumerRecords, KafkaConsumer}
import org.apache.kafka.common.serialization.{Deserializer, Serdes}
import org.apache.kafka.common.utils.Bytes
import org.slf4j.{Logger, LoggerFactory}
import pureconfig.ConfigSource
import pureconfig.generic.auto._
import ujson.Obj

import scala.collection.mutable
import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._
import scala.util.Try

object Consumer extends cask.MainRoutes with ScalaReflectionSerde with Configuration {

  val bookMap: mutable.Map[String, Book] = mutable.Map[String, Book]()

  private val consumerConf = ConfigSource.default.at("consumer").loadOrThrow[ConsumerConf]

  private val schemaRegistryConfigMap: Map[String, AnyRef] = Map[String, AnyRef](
    SCHEMA_REGISTRY_URL_CONFIG -> consumerConf.clientConfig.getString(SCHEMA_REGISTRY_URL_CONFIG)
  )

  override def port: Int = consumerConf.port

  override def host: String = consumerConf.host

  private def logger: Logger = LoggerFactory.getLogger(getClass)

  @cask.get("/count")
  def getCount(): Obj = {
    ujson.Obj("count" -> bookMap.size)
  }

  @cask.get("/books")
  def getBooks(): Obj = {
    ujson.Obj("results" -> ujson.Arr(
      bookMap.toIndexedSeq.map { case (_: String, book: Book) =>
        upickle.default.writeJs(book)
      }: _*
    ))
  }

  def consume(consumer: KafkaConsumer[Bytes, Book]): Vector[Book] = {

    val books: ConsumerRecords[Bytes, Book] = consumer.poll((1 second) toJava)

    books.asScala.toVector.map(_.value())
  }

  val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()
  scheduler.schedule(() => {
    logger debug "creating the deserializers and configuring"
    val bookDeserializer: Deserializer[Book] = reflectionDeserializer4S[Book]
    bookDeserializer.configure(schemaRegistryConfigMap.asJava, false)

    logger debug "creating the kafka consumer"
    val consumer = new KafkaConsumer[Bytes, Book](
      consumerConf.clientConfig.toProperties,
      Serdes.Bytes().deserializer(),
      bookDeserializer
    )

    consumer.subscribe(Vector(consumerConf.topics.bookTopic.name).asJava)

    while (!scheduler.isShutdown) {
      Thread.sleep((2 second) toMillis)

      logger debug s"polling the new events"
      val books: Vector[Book] = consume(consumer)

      if (books.nonEmpty) logger info s"just polled ${books.size} books from kafka"
      books.foreach { book =>
        bookMap += book.title -> book
      }
    }

    logger info "Closing the kafka consumer"
    Try(consumer.close()).recover {
      case error => logger.error("Failed to close the kafka consumer", error)
    }

  }, 0, TimeUnit.SECONDS)

  sys.addShutdownHook {
    scheduler.shutdown()
    scheduler.awaitTermination(10, TimeUnit.SECONDS)
  }

  logger info s"starting the HTTP server"
  initialize()
}