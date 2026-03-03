# Flink Integration Tests

This document describes the Flink test infrastructure and setup requirements.

## Architecture

The Flink integration tests use a **shared container singleton pattern** to prevent resource exhaustion:

- **SharedFlinkKafkaContainers**: Singleton that manages Kafka and Schema Registry containers
- **AbstractFlinkKafkaTest**: Base class for all Flink SQL tests
- **Topic Namespacing**: Automatic topic prefixing with test class name to prevent collisions

## Prerequisites

### Required: Enable Testcontainers Reuse

For tests to run reliably with `./gradlew clean test`, you **must** enable Testcontainers reuse globally.

Add this to `~/.testcontainers.properties`:
```properties
testcontainers.reuse.enable=true
```

**Why?** Gradle runs test tasks in separate JVM processes (workers). The singleton pattern only works within a single JVM. Testcontainers reuse allows containers to be shared across multiple JVM processes via Docker container labels.

**Verify your configuration:**
```bash
grep "testcontainers.reuse.enable" ~/.testcontainers.properties
```

Should output: `testcontainers.reuse.enable=true`

## Running Tests

### Run all Flink tests
```bash
./gradlew :aggregating-count:flinksql:test :aggregating-minmax:flinksql:test \
  :deduplication:flinksql:test :deduplication-windowed:flinksql:test \
  :joining-stream-stream:flinksql:test :multi-joins:flinksql:test \
  :session-windows:flinksql:test :tumbling-windows:flinksql:test \
  :hopping-windows:flinksql:test :cumulating-windows:flinksql:test \
  :windowed-top-N:flinksql:test :top-N:flinksql:test \
  :splitting:flinksql:test :merging:flinksql:test :sorting:flinksql:test \
  :array-expansion:flinksql:test :pattern-matching:flinksql:test \
  :over-aggregations:flinksql:test :lagging-events:flinksql:test \
  --parallel
```

### Run with clean
```bash
./gradlew clean [test tasks...] --parallel
```

## How It Works

### 1. Shared Containers
When the first test runs:
- `SharedFlinkKafkaContainers.getInstance()` starts Kafka + Schema Registry
- Containers are marked with `.withReuse(true)`
- Container labels are registered with Docker

Subsequent tests (even in different JVM processes):
- `getInstance()` finds existing containers via Docker labels
- Reuses the same Kafka + Schema Registry instances
- No additional container startup overhead

### 2. Topic Namespacing
To prevent data pollution when tests share containers:
- All topic names are automatically prefixed with the test class name
- Example: `'topic' = 'movie_views'` → `'topic' = 'FlinkSqlTopNTest-movie_views'`
- This happens transparently in `getResourceFileContents()`

### 3. Test Isolation
Each test class gets:
- Isolated topics (via namespacing)
- Shared Kafka/Schema Registry (via container reuse)
- Independent Flink table environment

## Troubleshooting

### "Container startup failed" errors with clean test
**Cause:** Testcontainers reuse is disabled
**Fix:** Enable `testcontainers.reuse.enable=true` in `~/.testcontainers.properties`

### Intermittent test failures
**Cause:** Topic name collisions (should not happen with namespacing)
**Check:** Verify topic namespacing is working by looking at Kafka topics during test execution

### Tests hanging
**Cause:** Resource exhaustion or port conflicts
**Fix:**
1. Stop all Docker containers: `docker stop $(docker ps -q)`
2. Verify no containers are reusing ports 9092 or 8081
3. Re-run tests

### Clean up reused containers
```bash
docker ps --filter "label=org.testcontainers.sessionId" -q | xargs docker stop
```

## Test Structure

All Flink SQL tests follow this pattern:
```java
public class MyFlinkSqlTest extends AbstractFlinkKafkaTest {

  @Test
  public void testSomething() throws Exception {
    // Create tables using getResourceFileContents()
    streamTableEnv.executeSql(getResourceFileContents(
      "create-table.sql.template",
      Optional.of(kafkaPort),
      Optional.of(schemaRegistryPort)
    )).await();

    // Populate test data
    streamTableEnv.executeSql(getResourceFileContents(
      "populate-data.sql"
    )).await();

    // Execute query and verify results
    TableResult result = streamTableEnv.executeSql(
      getResourceFileContents("query.sql")
    );

    // Assert results...
  }
}
```

## Benefits of This Architecture

✅ **Faster tests** - Containers start once, not 19 times
✅ **Reliable parallel execution** - No resource exhaustion
✅ **Complete isolation** - Topic namespacing prevents data pollution
✅ **No template changes** - Automatic namespacing in base class
✅ **Works with clean** - Testcontainers reuse across JVM workers
