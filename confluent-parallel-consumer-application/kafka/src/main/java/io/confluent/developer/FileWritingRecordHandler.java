package io.confluent.developer;


import org.apache.kafka.clients.consumer.ConsumerRecord;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.Collections.singletonList;


/**
 * Record handler that writes the Kafka message value to a file.
 */
public class FileWritingRecordHandler extends ConsumerRecordHandler<String, String> {

  private final Path path;

  public FileWritingRecordHandler(final Path path) {
    this.path = path;
  }

  @Override
  protected void processRecordImpl(final ConsumerRecord<String, String> consumerRecord) {
    try {
      Files.write(path, singletonList(consumerRecord.value()), CREATE, WRITE, APPEND);
    } catch (IOException e) {
      throw new RuntimeException("unable to write record to file", e);
    }
  }

}
