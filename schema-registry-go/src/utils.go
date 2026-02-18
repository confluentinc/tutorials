package main

import (
	"flag"
	"fmt"
	"log"
	"strings"

	"github.com/confluentinc/confluent-kafka-go/v2/kafka"
	"github.com/confluentinc/confluent-kafka-go/v2/schemaregistry"
	"github.com/magiconair/properties"
)

// ParseConfigFlags parses and validates the required kafka-properties-file and sr-properties-file flags
func ParseConfigFlags() (kafkaConfigFile, srConfigFile string) {
	kafkaConfigFilePtr := flag.String("kafka-properties-file", "", "Path to Kafka configuration (required)")
	srConfigFilePtr := flag.String("sr-properties-file", "", "Path to Schema Registry configuration (required)")
	flag.Parse()

	if *kafkaConfigFilePtr == "" || *srConfigFilePtr == "" {
		log.Fatalf("Error: Both -kafka-properties-file and -sr-properties-file flags are required")
	}

	return *kafkaConfigFilePtr, *srConfigFilePtr
}

// LoadKafkaConfig loads Kafka configuration from a properties file into a ConfigMap
func LoadKafkaConfig(configFile string) (kafka.ConfigMap, error) {
	kafkaConfig := kafka.ConfigMap{}
	kafkaProperties, err := properties.LoadFile(configFile, properties.UTF8)
	if err != nil {
		return nil, fmt.Errorf("failed to load Kafka config file: %w", err)
	}

	for _, key := range kafkaProperties.Keys() {
		kafkaConfig[key] = kafkaProperties.GetString(key, "")
	}

	return kafkaConfig, nil
}

// CreateSchemaRegistryClient creates a Schema Registry client with optional basic authentication
// The client config is dynamically created based on the presence of "basic.auth.user.info" in the properties file
func CreateSchemaRegistryClient(configFile string) (schemaregistry.Client, error) {
	srProperties, err := properties.LoadFile(configFile, properties.UTF8)
	if err != nil {
		return nil, fmt.Errorf("failed to load Schema Registry config file: %w", err)
	}

	url := srProperties.GetString("url", "")
	if url == "" {
		return nil, fmt.Errorf("url not found in Schema Registry config file")
	}

	var srConfig *schemaregistry.Config
	if basicAuthUserInfo := srProperties.GetString("basic.auth.user.info", ""); basicAuthUserInfo != "" {
		// split username:password
		parts := strings.SplitN(basicAuthUserInfo, ":", 2)
		if len(parts) != 2 {
			return nil, fmt.Errorf("invalid basic.auth.user.info format. Expected username:password")
		}
		username := parts[0]
		password := parts[1]
		srConfig = schemaregistry.NewConfigWithBasicAuthentication(url, username, password)
	} else {
		srConfig = schemaregistry.NewConfig(url)
	}

	client, err := schemaregistry.NewClient(srConfig)
	if err != nil {
		return nil, fmt.Errorf("failed to create schema registry client: %w", err)
	}

	return client, nil
}
