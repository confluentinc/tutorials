package io.confluent.developer.consume

import java.time.LocalDate

import io.confluent.developer.Configuration.{ProducerConf, TopicConf}
import io.confluent.developer.KafkaFlatSpec
import io.confluent.developer.schema.BookType.{Novel, Other, Tech}
import io.confluent.developer.schema.{Book, ScalaReflectionSerde}
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
import org.apache.kafka.clients.consumer.KafkaConsumer
import org.apache.kafka.clients.producer.{KafkaProducer, ProducerRecord}
import org.apache.kafka.common.TopicPartition
import org.apache.kafka.common.serialization.{Deserializer, Serdes, Serializer}
import org.apache.kafka.common.utils.Bytes
import pureconfig.ConfigSource
import pureconfig.generic.auto._

import scala.concurrent.duration._
import scala.jdk.CollectionConverters._
import scala.jdk.DurationConverters._

class ConsumerSpec extends KafkaFlatSpec with ScalaReflectionSerde {

  var testProducer: KafkaProducer[Bytes, Book] = _
  val conf: ProducerConf = ConfigSource.default.at("consumer").loadOrThrow[ProducerConf]

  val bookSerializer: Serializer[Book] = ScalaReflectionSerde.reflectionSerializer4S[Book]
  val bookDeserializer: Deserializer[Book] = ScalaReflectionSerde.reflectionDeserializer4S[Book]
  override val testTopics: Vector[TopicConf.TopicSpec] = conf.topics.all

  bookSerializer.configure(Map(SCHEMA_REGISTRY_URL_CONFIG -> "mock://unused:8081").asJava, false)
  bookDeserializer.configure(Map(SCHEMA_REGISTRY_URL_CONFIG -> "mock://unused:8081").asJava, false)

  override def beforeAll(): Unit = {
    super.beforeAll()
    val config = Map[String, AnyRef]("group.id" -> "test", "bootstrap.servers" -> kafka.getBootstrapServers)
    testProducer = new KafkaProducer[Bytes, Book](config.asJava, Serdes.Bytes().serializer(), bookSerializer)
  }

  override def afterAll(): Unit = {
    testProducer.close()
    super.afterAll()
  }

  "consume" should "fetch the existing records from kafka" in {

    Given("a consumer config")
    val config = Map[String, AnyRef]("client.id" -> "test", "bootstrap.servers" -> kafka.getBootstrapServers)
    val consumer = new KafkaConsumer[Bytes, Book](config.asJava, Serdes.Bytes().deserializer(), bookDeserializer)

    And("a collection of books")
    val newBook1 = Book("book1", "title1", Tech, 20, LocalDate.of(2020, 4, 1))
    val newBook2 = Book("book2", "title2", Novel, 300, LocalDate.of(2020, 5, 1))
    val newBook3 = Book("book3", "title3", Other, 888, LocalDate.of(2020, 6, 1))

    testProducer.send(new ProducerRecord(conf.topics.bookTopic.name, newBook1))
    testProducer.send(new ProducerRecord(conf.topics.bookTopic.name, newBook2))
    testProducer.send(new ProducerRecord(conf.topics.bookTopic.name, newBook3))

    testProducer.flush()

    When("we consume back the records")
    val topicPartitions: Seq[TopicPartition] = (0 until conf.topics.bookTopic.partitions)
      .map(new TopicPartition(conf.topics.bookTopic.name, _))

    consumer.assign(topicPartitions.asJava)

    Then("a collection of books is returned")
    eventually(timeout(5 second),  interval(1 second)){
      consumer.seekToBeginning(topicPartitions.asJava)
      val records: List[Book] = consumer.poll((1 second) toJava).asScala.map(_.value()).toList

      records should have length 3
      records should contain theSameElementsAs(newBook1 :: newBook2 :: newBook3 :: Nil)
    }
  }
}
