STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/multiple-event-types/kafka.yml $(TEMP_DIR)
	diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-output-protobuf.txt $(DEV_OUTPUTS_DIR)/actual-output-protobuf.txt
	diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-output-avro.txt $(DEV_OUTPUTS_DIR)/actual-output-avro.txt
	reset
