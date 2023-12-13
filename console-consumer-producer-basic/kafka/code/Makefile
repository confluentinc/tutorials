STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/console-consumer-producer-basic/kafka.yml $(TEMP_DIR) $(SEQUENCE)
	#bash -c "diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-output-step-one.txt <(head -n 4 $(DEV_OUTPUTS_DIR)/actual-output-step-one.txt)"
	bash -c "diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-output-step-two.txt <(head -n 8 $(DEV_OUTPUTS_DIR)/actual-output-step-two.txt)"
	bash -c "diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-output-step-three.txt <(head -n 12 $(DEV_OUTPUTS_DIR)/actual-output-step-three.txt)"



	reset
