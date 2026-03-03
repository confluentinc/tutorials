# Flink Integration Tests

This document describes the Flink test infrastructure, setup requirements, and best practices.

## Architecture

The Flink integration tests use a **shared container singleton pattern** to prevent resource exhaustion:

- **SharedFlinkKafkaContainers**: Singleton that manages Kafka and Schema Registry containers
- **AbstractFlinkKafkaTest**: Base class for all Flink SQL tests
- **Topic Namespacing**: Automatic topic prefixing with test class name to prevent collisions
- **Retry Logic**: Exponential backoff when container creation fails

## Prerequisites

### Required: Enable Testcontainers Reuse

You **must** enable Testcontainers reuse globally for reliable test execution.

Add this to `~/.testcontainers.properties`:
```properties
testcontainers.reuse.enable=true
```

**Verify your configuration:**
```bash
grep "testcontainers.reuse.enable" ~/.testcontainers.properties
```

Should output: `testcontainers.reuse.enable=true`

## Running Tests

### ⚠️ Important: Avoid High Parallelism with `clean`

When running `./gradlew clean test --parallel` with many workers, you may encounter occasional container timeout failures due to race conditions during container creation across multiple JVM processes.

### Recommended Approaches

**Option 1: Run without `--parallel` flag (most reliable)**
```bash
./gradlew clean :aggregating-count:flinksql:test :aggregating-minmax:flinksql:test \
  :deduplication:flinksql:test [... other tests ...]
```

**Option 2: Run in smaller batches (good balance)**
```bash
# Batch 1 - Window tests (5 tests)
./gradlew :tumbling-windows:flinksql:test :hopping-windows:flinksql:test \
  :session-windows:flinksql:test :cumulating-windows:flinksql:test \
  :windowed-top-N:flinksql:test --parallel --max-workers=2

# Batch 2 - Join tests (2 tests)
./gradlew :joining-stream-stream:flinksql:test :multi-joins:flinksql:test \
  --parallel --max-workers=2

# Batch 3 - Aggregation tests (4 tests)
./gradlew :aggregating-count:flinksql:test :aggregating-minmax:flinksql:test \
  :over-aggregations:flinksql:test :top-N:flinksql:test \
  --parallel --max-workers=2
```

**Option 3: Lower parallelism (if you must use --parallel)**
```bash
./gradlew clean [all test tasks] --parallel --max-workers=2
```

**Option 4: Accept occasional retries**
- The code includes retry logic with exponential backoff (5s, 10s)
- Most failures will automatically retry and succeed
- If a test fails, re-run that specific test

## How It Works

### 1. Shared Containers Within JVM
Within a single JVM (Gradle worker):
- `SharedFlinkKafkaContainers.getInstance()` creates containers once
- All test classes in that worker share the same containers

### 2. Retry Logic Across JVMs
When multiple workers start simultaneously:
- Each tries to create containers with `.withReuse(true)`
- If creation fails (race condition), retry after 5s, then 10s
- Usually one worker succeeds, others eventually reuse

### 3. Topic Namespacing
To prevent data pollution:
- All topic names automatically prefixed: `TestClassName-topicname`
- Example: `'topic' = 'clicks'` → `'topic' = 'FlinkSqlDeduplicationTest-clicks'`
- Happens transparently in `getResourceFileContents()`

### 4. Configuration
- **Container startup timeout**: 60 seconds (Schema Registry)
- **Retry attempts**: 3 with exponential backoff (5s, 10s)
- **No shutdown hooks**: Containers stay running for reuse

## Troubleshooting

### Occasional "Container startup failed" with --parallel
**Cause:** Race condition when multiple workers create containers simultaneously
**Solutions:**
1. Re-run the failed test (retry logic should help)
2. Run without `--parallel` flag
3. Use `--max-workers=2` to reduce contention

### Consistent failures on single test
**Cause:** Test-specific issue, not infrastructure
**Debug:**
1. Run the test alone: `./gradlew :module:flinksql:test`
2. Check container logs: `docker logs $(docker ps -q --filter "label=flink-test-containers")`
3. Verify ports aren't blocked: `lsof -i :9092` and `lsof -i :8081`

### Tests hanging
**Cause:** Resource exhaustion or port conflicts
**Fix:**
```bash
# Stop all test containers
docker ps --filter "label=flink-test-containers" -q | xargs docker stop

# Or stop all containers
docker stop $(docker ps -q)
```

### Clean up reused containers manually
```bash
docker ps -a --filter "label=org.testcontainers.reuse" -q | xargs docker rm -f
```

## Benefits

✅ **Faster than serial execution** - Containers start once per worker
✅ **Complete test isolation** - Topic namespacing prevents data pollution
✅ **Automatic retry** - Handles race conditions gracefully
✅ **No template changes** - Works with existing SQL files
✅ **Works with reuse** - Containers persist across test runs

## Known Limitations

- **High parallelism may cause occasional timeouts** when running with `clean` and `--parallel --max-workers=4+`
- **Testcontainers reuse across JVMs** is not 100% reliable with dynamic networks
- **Recommended**: Run tests in batches or without `--parallel` for most reliable execution
