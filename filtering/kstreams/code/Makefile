STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/filtering/kstreams.yml $(TEMP_DIR) $(SEQUENCE)
	diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-output-events.json $(DEV_OUTPUTS_DIR)/actual-output-events.json
	reset
