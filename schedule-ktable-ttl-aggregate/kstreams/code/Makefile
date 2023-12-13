STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/schedule-ktable-ttl-aggregate/kstreams.yml $(TEMP_DIR) $(SEQUENCE)
	bash -c "diff --strip-trailing-cr  <(tail -n 1 $(STEPS_DIR)/dev/expected-output.txt) <(tail -n 1 $(DEV_OUTPUTS_DIR)/join-output-2.txt)"
	reset
