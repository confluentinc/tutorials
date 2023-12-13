package io.confluent.developer.produce

import java.time.LocalDate

import io.confluent.developer.Configuration.{ProducerConf, TopicConf}
import io.confluent.developer.KafkaFlatSpec
import io.confluent.developer.schema.BookType.{Novel, Other, Tech}
import io.confluent.developer.schema.{Book, ScalaReflectionSerde}
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{Deserializer, Serdes, Serializer}
import org.apache.kafka.common.utils.Bytes
import pureconfig.ConfigSource
import pureconfig.generic.auto._

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

class ProducerSpec extends KafkaFlatSpec with ScalaReflectionSerde {

  var testConsumer: KafkaConsumer[Bytes, Book] = _
  val bookSerializer: Serializer[Book] = ScalaReflectionSerde.reflectionSerializer4S[Book]
  val bookDeserializer: Deserializer[Book] = ScalaReflectionSerde.reflectionDeserializer4S[Book]
  val conf: ProducerConf = ConfigSource.default.at("producer").loadOrThrow[ProducerConf]

  override val testTopics: Vector[TopicConf.TopicSpec] = conf.topics.all

  bookSerializer.configure(Map(SCHEMA_REGISTRY_URL_CONFIG -> "mock://unused:8081").asJava, false)
  bookDeserializer.configure(Map(SCHEMA_REGISTRY_URL_CONFIG -> "mock://unused:8081").asJava, false)

  override def beforeAll(): Unit = {
    super.beforeAll()
    val config = Map[String, AnyRef]("group.id" -> "test", "bootstrap.servers" -> kafka.getBootstrapServers)
    testConsumer = new KafkaConsumer[Bytes, Book](config.asJava, Serdes.Bytes().deserializer(), bookDeserializer)
  }

  "produce" should "write a series of new books to kafka" in {

    Given("a producer config")
    val config = Map[String, AnyRef]("client.id" -> "test", "bootstrap.servers" -> kafka.getBootstrapServers)
    val producer = new KafkaProducer[Bytes, Book](config.asJava, Serdes.Bytes().serializer(), bookSerializer)

    And("a collection of books")
    val newBook1 = Book("book1", "title1", Tech, 20, LocalDate.of(2020, 1, 1))
    val newBook2 = Book("book2", "title2", Novel, 300, LocalDate.of(2020, 2, 1))
    val newBook3 = Book("book3", "title3", Other, 888, LocalDate.of(2020, 3, 1))

    When("the books get produced")
    val maybeMetadata1 = Producer.produce(producer, conf.topics.bookTopic.name, newBook1)
    val maybeMetadata2 = Producer.produce(producer, conf.topics.bookTopic.name, newBook2)
    val maybeMetadata3 = Producer.produce(producer, conf.topics.bookTopic.name, newBook3)

    val topicPartitions: Seq[TopicPartition] = (0 until conf.topics.bookTopic.partitions)
      .map(new TopicPartition(conf.topics.bookTopic.name, _))

    testConsumer.assign(topicPartitions.asJava)

    Then("records can be fetched from Kafka")
    eventually(timeout(5 second),  interval(1 second)){
      testConsumer.seekToBeginning(topicPartitions.asJava)
      val records: List[Book] = testConsumer.poll((1 second) toJava).asScala.map(_.value()).toList

      records should have length 3
      records should contain theSameElementsAs(newBook1 :: newBook2 :: newBook3 :: Nil)

      forAll (maybeMetadata1 :: maybeMetadata2 :: maybeMetadata3 :: Nil) { metadata =>
        metadata.isDone shouldBe true
      }
    }

    producer.flush()
    producer.close()
  }
}
