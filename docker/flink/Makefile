FLINK_VERSION ?= 1.19.1-scala_2.12-java17
BUILD_PLATFORM ?= linux/amd64
PUSH_PREFIX ?= cnfldemos

build-flink-kafka:
	@docker buildx build --load --platform $(BUILD_PLATFORM) -t flink-kafka:$(FLINK_VERSION)  -f Dockerfile.flink-kafka .

build-flink-sql-client-kafka:
	@docker buildx build --load --platform $(BUILD_PLATFORM) -t flink-sql-client-kafka:$(FLINK_VERSION)  -f Dockerfile.flink-sql-client-kafka .

push-flink-kafka:
	@docker buildx build --push --platform linux/amd64,linux/arm64 -t $(PUSH_PREFIX)/flink-kafka:$(FLINK_VERSION)  -f Dockerfile.flink-kafka .

push-flink-sql-client-kafka:
	@docker buildx build --push --platform linux/amd64,linux/arm64 -t $(PUSH_PREFIX)/flink-sql-client-kafka:$(FLINK_VERSION)  -f Dockerfile.flink-sql-client-kafka .
