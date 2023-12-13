package io.confluent.developer;


import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;


public class FileWritingRecordHandlerTest {

  @Test
  public void testProcess() throws IOException {
    final Path tempFilePath = Files.createTempFile("test-handler", ".out");
    try {
      final ConsumerRecordHandler<String, String> recordHandler = new FileWritingRecordHandler(tempFilePath);
      ConsumerRecord record = new ConsumerRecord<>("test", 0, 0, null, "my record");
      recordHandler.processRecord(record);
      final List<String> expectedWords = Arrays.asList("my record");
      List<String> actualRecords = Files.readAllLines(tempFilePath);
      assertThat(actualRecords, equalTo(expectedWords));
    } finally {
      Files.deleteIfExists(tempFilePath);
    }
  }

}
