STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, ccloud"

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/versioned-ktables/kstreams.yml $(TEMP_DIR) $(SEQUENCE)
	diff --ignore-blank-lines --strip-trailing-cr $(STEPS_DIR)/dev/harness-expected-output.txt $(DEV_OUTPUTS_DIR)/actual-output.txt
	diff --ignore-blank-lines --strip-trailing-cr $(STEPS_DIR)/dev/harness-expected-output-validate-table.txt $(DEV_OUTPUTS_DIR)/actual-output-validate-table.txt
	reset
