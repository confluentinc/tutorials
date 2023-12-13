STEPS_DIR := tutorial-steps
TEST_OUTPUTS_DIR := $(STEPS_DIR)/test/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "test"

tutorial:
	rm -r $(TEST_OUTPUTS_DIR) || true
	mkdir -p $(TEST_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/inventory/ksql-test.yml $(TEMP_DIR) $(SEQUENCE)
	# Ensure device 1 is lost
	grep '^|1 ' $(TEST_OUTPUTS_DIR)/validate/output-0.log || { cat $(TEST_OUTPUTS_DIR)/validate/output-0.log ; false; }
	# Ensure device 0 is not lost
	grep '^|0 ' $(TEST_OUTPUTS_DIR)/validate/output-0.log || [ $$? -ne 0 ] || { cat $(TEST_OUTPUTS_DIR)/validate/output-0.log ; false; }
	reset
