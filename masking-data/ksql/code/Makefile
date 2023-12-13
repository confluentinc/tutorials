STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEST_OUTPUTS_DIR := $(STEPS_DIR)/test/outputs
PROD_OUTPUTS_DIR := $(STEPS_DIR)/prod/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	rm -r $(TEST_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	mkdir $(TEST_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/masking-data/ksql.yml $(TEMP_DIR) $(SEQUENCE)
	diff --strip-trailing-cr --ignore-space-change $(STEPS_DIR)/dev/expected-purchases-output.log $(DEV_OUTPUTS_DIR)/print-purchases-topic/output-0.log
	diff --strip-trailing-cr --ignore-space-change $(STEPS_DIR)/dev/expected-purchases-removed-output.log $(DEV_OUTPUTS_DIR)/print-purchases-removed-topic/output-0.log
	diff --strip-trailing-cr --ignore-space-change $(STEPS_DIR)/dev/expected-purchases-obfuscated-output.log $(DEV_OUTPUTS_DIR)/print-purchases-obfuscated-topic/output-0.log
	reset
