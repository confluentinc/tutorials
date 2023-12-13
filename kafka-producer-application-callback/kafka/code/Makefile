STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	cp $(STEPS_DIR)/dev/input.txt $(TEMP_DIR)
	harness-runner ../../../../../_data/harnesses/kafka-producer-application-callback/kafka.yml $(TEMP_DIR) $(SEQUENCE)
	diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-output.txt $(DEV_OUTPUTS_DIR)/actual-output.txt
	reset
	
