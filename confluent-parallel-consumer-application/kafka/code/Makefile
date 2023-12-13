STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/confluent-parallel-consumer-application/kafka.yml $(TEMP_DIR)
	bash -c 'diff --strip-trailing-cr <(sort $(STEPS_DIR)/dev/expected-output.txt) <(sort $(DEV_OUTPUTS_DIR)/actual-output.txt)'
	reset
