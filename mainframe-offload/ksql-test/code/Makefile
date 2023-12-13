STEPS_DIR := tutorial-steps
TEST_OUTPUTS_DIR := $(STEPS_DIR)/test/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "test"

tutorial:
	rm -r $(TEST_OUTPUTS_DIR) || true
	mkdir -p $(TEST_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/inventory/ksql-test.yml $(TEMP_DIR) $(SEQUENCE)
	# Read entry
	grep N839327 $(TEST_OUTPUTS_DIR)/validate/output-0.log | grep 5075 || { cat $(TEST_OUTPUTS_DIR)/validate/output-0.log ; false; }
	reset
