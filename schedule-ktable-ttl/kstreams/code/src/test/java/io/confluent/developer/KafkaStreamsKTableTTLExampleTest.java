package io.confluent.developer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.TestInputTopic;
import org.apache.kafka.streams.TestOutputTopic;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.TopologyTestDriver;
import org.junit.Test;

public class KafkaStreamsKTableTTLExampleTest {

  private final static String TEST_CONFIG_FILE = "configuration/test.properties";

  @Test
  public void shouldTriggerStreamTableJoinFromTable() throws Exception {
  
    final KafkaStreamsKTableTTLExample instance = new KafkaStreamsKTableTTLExample();
    final Properties envProps = instance.loadEnvProperties(TEST_CONFIG_FILE);

    final Properties streamProps = instance.getStreamProps(envProps);
    final String inputTopicName = envProps.getProperty("input.topic.name");
    final String outputTopicName = envProps.getProperty("output.topic.name");
    final String tableTopicName = envProps.getProperty("table.topic.name");

    final Topology topology = instance.buildTopology(envProps);
    System.out.println(topology);
    try (final TopologyTestDriver testDriver = new TopologyTestDriver(topology, streamProps)) {


      final Serializer<String> keySerializer = Serdes.String().serializer();
      final Serializer<String> valueSerializer = Serdes.String().serializer();
      
      final Deserializer<String> keyDeserializer = Serdes.String().deserializer();
      final Deserializer<String> valueDeserializer = Serdes.String().deserializer();

      
      final TestInputTopic<String, String>  inputTopic = testDriver.createInputTopic(inputTopicName,
                                                                                        keySerializer,
                                                                                        valueSerializer);
      final TestInputTopic<String, String>  tableTopic = testDriver.createInputTopic(tableTopicName,
          keySerializer,
          valueSerializer);

      final TestOutputTopic<String, String> outputTopic = testDriver.createOutputTopic(outputTopicName, keyDeserializer, valueDeserializer);
      final TestOutputTopic<String, String> outputTableTopic = testDriver.createOutputTopic(tableTopicName, keyDeserializer, valueDeserializer);

      
      tableTopic.pipeInput("alice", "a", 5);
      tableTopic.pipeInput("bobby", "b", 10);
      

      inputTopic.pipeInput("alice", "11", 10);
      inputTopic.pipeInput("bobby", "21", 15);
      inputTopic.pipeInput("alice", "12", 30);
      

      tableTopic.pipeInput("freddy", "f", 65000);  // punctuate gets called now

      inputTopic.pipeInput("alice", "13", 70006);
      inputTopic.pipeInput("bobby", "22", 70016);

 
      final List<KeyValue<String, String>> actualResults = outputTopic.readKeyValuesToList();

      assertThat(actualResults.size(), is(5));
      
      assertThat(actualResults.get(0).key, is("alice"));
      assertThat(actualResults.get(0).value, is("11 a"));
      
      assertThat(actualResults.get(1).key, is("bobby"));
      assertThat(actualResults.get(1).value, is("21 b"));

      assertThat(actualResults.get(2).key, is("alice"));
      assertThat(actualResults.get(2).value, is("12 a"));
      
      assertThat(actualResults.get(3).key, is("alice"));
      assertThat(actualResults.get(3).value, is("13")); // join didn't match on right side
      
      
      assertThat(actualResults.get(4).key, is("bobby"));
      assertThat(actualResults.get(4).value, is("22"));
      
      
  }
    
  }
  
}
