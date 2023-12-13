package io.confluent.developer.schema

import com.sksamuel.avro4s.RecordFormat
import io.confluent.kafka.streams.serdes.avro.{GenericAvroDeserializer, GenericAvroSerializer}
import org.apache.kafka.common.serialization.{Deserializer, Serializer}

trait ScalaReflectionSerde {

  implicit lazy val bookFormat: RecordFormat[Book] = RecordFormat[Book]
}

object ScalaReflectionSerde {

  def reflectionSerializer4S[T: RecordFormat]: Serializer[T] = new Serializer[T] {
    val inner = new GenericAvroSerializer()

    override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = inner.configure(configs, isKey)

    override def serialize(topic: String, maybeData: T): Array[Byte] = Option(maybeData)
      .map(data => inner.serialize(topic, implicitly[RecordFormat[T]].to(data)))
      .getOrElse(Array.emptyByteArray)

    override def close(): Unit = inner.close()
  }

  def reflectionDeserializer4S[T: RecordFormat]: Deserializer[T] = new Deserializer[T] {
    val inner = new GenericAvroDeserializer()

    override def configure(configs: java.util.Map[String, _], isKey: Boolean): Unit = inner.configure(configs, isKey)

    override def deserialize(topic: String, maybeData: Array[Byte]): T = Option(maybeData)
      .filter(_.nonEmpty)
      .map(data => implicitly[RecordFormat[T]].from(inner.deserialize(topic, data)))
      .getOrElse(null.asInstanceOf[T])

    override def close(): Unit = inner.close()
  }
}