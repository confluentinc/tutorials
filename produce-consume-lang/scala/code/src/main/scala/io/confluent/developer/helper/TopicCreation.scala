package io.confluent.developer.helper

import java.util.concurrent.ExecutionException

import io.confluent.developer.Configuration
import io.confluent.developer.Configuration.HelperConf
import org.apache.kafka.clients.admin.{Admin, CreateTopicsResult, NewTopic}
import org.apache.kafka.common.errors.TopicExistsException
import org.slf4j.{Logger, LoggerFactory}
import pureconfig.ConfigSource
import pureconfig.generic.auto._

import scala.concurrent.duration.TimeUnit
import scala.jdk.CollectionConverters._
import scala.util.{Failure, Success, Try}

object TopicCreation extends App with Configuration {

  private val logger: Logger = LoggerFactory.getLogger(getClass)

  val helperConf = ConfigSource.default.at("helper").loadOrThrow[HelperConf]

  val client = Admin.create(helperConf.clientConfig.toMap.asJava)

  val newTopics = helperConf
    .topics
    .all
    .map { topic =>
      new NewTopic(topic.name, topic.partitions, topic.replicationFactor)
    }

  logger.info(s"Starting the topics creation for: ${helperConf.topics.all.map(_.name).mkString(", ")}")

  val allKFutures: CreateTopicsResult = client.createTopics(newTopics.asJava)

  allKFutures.values().asScala.foreach { case (topicName, kFuture) =>

    kFuture.whenComplete {

      case (_, throwable: Throwable) if Option(throwable).isDefined =>
        logger.warn("Topic creation didn't complete:", throwable)

      case _ =>
        newTopics.find(_.name() == topicName).map { topic =>
          logger.info(
            s"""|Topic ${topic.name}
                | has been successfully created with ${topic.numPartitions} partitions
                | and replicated ${topic.replicationFactor() - 1} times""".stripMargin.replaceAll("\n", "")
          )
        }
    }
  }

  val (timeOut, timeUnit): (Long, TimeUnit) = helperConf.topicCreationTimeout

  Try(allKFutures.all().get(timeOut, timeUnit)) match {

    case Failure(ex) if ex.getCause.isInstanceOf[TopicExistsException] =>
      logger info "Topic creation stage completed. (Topics already created)"

    case failure@Failure(_: InterruptedException | _: ExecutionException) =>
      logger error "The topic creation failed to complete"
      failure.exception.printStackTrace()
      sys.exit(2)

    case Failure(exception) =>
      logger error "The following exception occurred during the topic creation"
      exception.printStackTrace()
      sys.exit(3)

    case Success(_) =>
      logger info "Topic creation stage completed."
  }
}
