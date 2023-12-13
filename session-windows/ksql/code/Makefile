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
	harness-runner ../../../../../_data/harnesses/session-windows/ksql.yml $(TEMP_DIR) $(SEQUENCE)
	bash -c "diff --strip-trailing-cr <(sort $(STEPS_DIR)/dev/expected-check-rowtime.log | wc -l) <(sort $(DEV_OUTPUTS_DIR)/check-rowtime/*.log | wc -l)"
	bash -c "diff --strip-trailing-cr <(sort $(STEPS_DIR)/dev/expected-transient-query.log | wc -l) <(sort $(DEV_OUTPUTS_DIR)/transient-query/*.log | wc -l)"
	bash -c "diff --strip-trailing-cr <(cut -d ',' -f 2- $(STEPS_DIR)/dev/expected-print-topic.log | wc -l) <(cut -d ',' -f 2- $(DEV_OUTPUTS_DIR)/print-topic/*.log | wc -l)"
	diff --strip-trailing-cr $(STEPS_DIR)/test/expected-results.log $(TEST_OUTPUTS_DIR)/test-results.log
	reset
