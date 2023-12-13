STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/creating-first-apache-kafka-streams-application/kstreams.yml $(TEMP_DIR) $(SEQUENCE)
#diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-output.json $(DEV_OUTPUTS_DIR)/actual-output.json
#diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-special-output.json $(DEV_OUTPUTS_DIR)/actual-special-order-output.json	
	reset
