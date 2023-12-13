package io.confluent.developer

import io.confluent.developer.Configuration.TopicConf.TopicSpec
import org.apache.kafka.clients.admin.{Admin, NewTopic}
import org.junit.Rule
import org.scalatest.concurrent.Eventually
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, GivenWhenThen, Inspectors}
import org.testcontainers.containers.KafkaContainer
import org.testcontainers.utility.DockerImageName

import scala.jdk.CollectionConverters._

trait KafkaFlatSpec extends AnyFlatSpec
  with Matchers
  with Inspectors
  with BeforeAndAfterAll
  with GivenWhenThen
  with Eventually {

  val testTopics: Vector[TopicSpec]

  @Rule
  val kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.3.0"))
  lazy val admin: Admin = Admin.create(Map[String, AnyRef]("bootstrap.servers" -> kafka.getBootstrapServers).asJava)

  override def beforeAll(): Unit = {
    super.beforeAll()
    kafka.start()
    admin.createTopics(
      testTopics.map { topic =>
        new NewTopic(
          topic.name,
          topic.partitions,
          topic.replicationFactor
        )
      }.asJava
    )
  }

  override def afterAll(): Unit = {
    admin.close()
    kafka.stop()
    super.afterAll()
  }
}
