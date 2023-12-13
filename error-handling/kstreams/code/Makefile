STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"


tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/error-handling/kstreams.yml $(TEMP_DIR) $(SEQUENCE)
	bash -c "diff --strip-trailing-cr  <(tail -n 1 $(STEPS_DIR)/dev/expected-output.txt) <(tail -n 1 $(DEV_OUTPUTS_DIR)/actual-output.txt)"
	bash -c "grep 'BUILD SUCCESSFUL' $(DEV_OUTPUTS_DIR)/unit-test-output.txt"
	reset
