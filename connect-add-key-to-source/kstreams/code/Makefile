STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/connect-add-key-to-source/kstreams.yml $(TEMP_DIR) $(SEQUENCE)
	bash -c "diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-input-events.log <(grep city_id $(DEV_OUTPUTS_DIR)/actual-input-events.log)"
	bash -c "diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-output-events.log <(grep city_id $(DEV_OUTPUTS_DIR)/actual-output-events.log)"
	reset
