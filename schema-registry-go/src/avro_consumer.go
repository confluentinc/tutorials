package main

import (
	"log"
	"os"
	"os/signal"
	"syscall"

	"github.com/confluentinc/confluent-kafka-go/v2/kafka"
	"github.com/confluentinc/confluent-kafka-go/v2/schemaregistry/serde"
	"github.com/confluentinc/confluent-kafka-go/v2/schemaregistry/serde/avrov2"
)

func main() {

	sigchan := make(chan os.Signal, 1)
	signal.Notify(sigchan, syscall.SIGINT, syscall.SIGTERM)

	kafkaConfigFile, srConfigFile := ParseConfigFlags()

	kafkaConfig, err := LoadKafkaConfig(kafkaConfigFile)
	if err != nil {
		log.Fatalf("Failed to load Kafka config: %s", err)
	}

	kafkaConfig["group.id"] = "kafka-sr-go-group"
	kafkaConfig["auto.offset.reset"] = "earliest"
	c, err := kafka.NewConsumer(&kafkaConfig)
	if err != nil {
		log.Fatalf("Failed to create consumer: %s", err)
	}

	client, err := CreateSchemaRegistryClient(srConfigFile)
	if err != nil {
		log.Fatalf("Failed to create schema registry client: %s", err)
	}

	deser, err := avrov2.NewDeserializer(client, serde.ValueSerde, avrov2.NewDeserializerConfig())
	if err != nil {
		log.Fatalf("Failed to create deserializer: %s", err)
	}

	err = c.SubscribeTopics([]string{"readings"}, nil)
	if err != nil {
		log.Fatalf("Failed to subscribe to topics: %s", err)
	}

	run := true

	for run {
		select {
		case sig := <-sigchan:
			log.Printf("Caught signal %v: terminating", sig)
			run = false
		default:
			ev := c.Poll(100)
			if ev == nil {
				continue
			}

			switch e := ev.(type) {
			case *kafka.Message:
				value := TempReading{}
				err := deser.DeserializeInto(*e.TopicPartition.Topic, e.Value, &value)
				if err != nil {
					log.Printf("Failed to deserialize payload: %s", err)
				} else {
					log.Printf("%+v", value)
				}
			case kafka.Error:
				// Errors should generally be considered
				// informational, the client will try to
				// automatically recover.
				log.Printf("Error: %v: %v", e.Code(), e)
			case kafka.OffsetsCommitted:
				// ignore without output
			default:
				log.Printf("Ignored %v", e)
			}
		}
	}

	log.Println("Closing consumer")
	c.Close()
}
