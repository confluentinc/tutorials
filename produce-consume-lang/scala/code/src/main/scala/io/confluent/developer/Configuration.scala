package io.confluent.developer

import java.util.Properties

import com.typesafe.config.Config
import io.confluent.developer.Configuration.TopicConf.TopicSpec

import scala.concurrent.duration.Duration
import scala.jdk.CollectionConverters._

object Configuration {

  case class ProducerConf(clientConfig: Config, topics: TopicConf)

  case class ConsumerConf(clientConfig: Config, topics: TopicConf, host: String, port: Int)

  case class HelperConf(clientConfig: Config,
                        topics: TopicConf,
                        topicCreationTimeout: Duration,
                        schemaRegistryRetriesNum: Int,
                        schemaRegistryRetriesInterval: Duration)

  case class TopicConf(bookTopic: TopicSpec) {
    def all: Vector[TopicSpec] = Vector(bookTopic)
  }

  object TopicConf {
    case class TopicSpec(name: String, partitions: Int, replicationFactor: Short)
  }
}

trait Configuration {
  implicit class configMapperOps(config: Config) {

    def toMap: Map[String, AnyRef] = config
      .entrySet()
      .asScala
      .map(pair => (pair.getKey, config.getAnyRef(pair.getKey)))
      .toMap

    def toProperties: Properties = {
      val properties = new Properties()
      properties.putAll(config.toMap.asJava)
      properties
    }
  }
}