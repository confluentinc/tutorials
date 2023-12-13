STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/console-consumer-producer-avro/kafka.yml $(TEMP_DIR) $(SEQUENCE)
	bash -c "diff -B --strip-trailing-cr tutorial-steps/dev/expected-records-full.log tutorial-steps/dev/outputs/actual-output-step-three.txt"  
	reset
