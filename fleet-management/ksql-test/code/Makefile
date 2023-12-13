STEPS_DIR := tutorial-steps
TEST_OUTPUTS_DIR := $(STEPS_DIR)/test/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "test"

tutorial:
	rm -r $(TEST_OUTPUTS_DIR) || true
	mkdir -p $(TEST_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/fleet-management/ksql-test.yml $(TEMP_DIR) $(SEQUENCE)
	# Check for existence of a latitude value (from stream side of join)
	grep 16.004175 $(TEST_OUTPUTS_DIR)/validate/output-0.log || { cat $(TEST_OUTPUTS_DIR)/validate/output-0.log ; false; }
	# Check for existence of a license value (from table side of join); only warn if not found since ksqlDB behavior doesn't guarantee this test case.
	grep 8852693196 $(TEST_OUTPUTS_DIR)/validate/output-0.log || { echo 'Warning: missing expected value from table side of join\n'; cat $(TEST_OUTPUTS_DIR)/validate/output-0.log ; true; }
	reset
