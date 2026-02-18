package main

import (
	"log"
	"math/rand"
	"strconv"

	"github.com/confluentinc/confluent-kafka-go/v2/kafka"
	"github.com/confluentinc/confluent-kafka-go/v2/schemaregistry/serde"
	"github.com/confluentinc/confluent-kafka-go/v2/schemaregistry/serde/avrov2"
)

func main() {

	kafkaConfigFile, srConfigFile := ParseConfigFlags()

	kafkaConfig, err := LoadKafkaConfig(kafkaConfigFile)
	if err != nil {
		log.Fatalf("Failed to load Kafka config: %s", err)
	}

	p, err := kafka.NewProducer(&kafkaConfig)
	if err != nil {
		log.Fatalf("Failed to create producer: %s", err)
	}

	client, err := CreateSchemaRegistryClient(srConfigFile)
	if err != nil {
		log.Fatalf("Failed to create schema registry client: %s", err)
	}

	ser, err := avrov2.NewSerializer(client, serde.ValueSerde, avrov2.NewSerializerConfig())
	if err != nil {
		log.Fatalf("Failed to create serializer: %s", err)
	}

	deliveryChan := make(chan kafka.Event)

	topic := "readings"

	for i := 0; i < 10; i++ {
		deviceId := strconv.Itoa(rand.Intn(4) + 1)
		temperature := 50.0 + rand.Float64()*50.0

		value := TempReading{
			DeviceID:    deviceId,
			Temperature: float32(temperature),
		}

		payload, err := ser.Serialize(topic, &value)
		if err != nil {
			log.Fatalf("Failed to serialize payload: %s", err)
		}

		err = p.Produce(&kafka.Message{
			TopicPartition: kafka.TopicPartition{Topic: &topic, Partition: kafka.PartitionAny},
			Value:          payload,
		}, deliveryChan)
		if err != nil {
			log.Fatalf("Produce failed: %v", err)
		}

		e := <-deliveryChan
		m := e.(*kafka.Message)

		if m.TopicPartition.Error != nil {
			log.Printf("Delivery failed: %v", m.TopicPartition.Error)
		} else {
			log.Printf("Delivered message to topic %s [%d] at offset %v: DeviceId=%s, Temperature=%.2f",
				*m.TopicPartition.Topic, m.TopicPartition.Partition, m.TopicPartition.Offset, deviceId, temperature)
		}
	}

	p.Flush(5000)
	p.Close()
	close(deliveryChan)
}
