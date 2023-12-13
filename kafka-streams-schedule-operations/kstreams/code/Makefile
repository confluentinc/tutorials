STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	cp $(STEPS_DIR)/dev/datagen-logintime.avsc $(TEMP_DIR)
	harness-runner ../../../../../_data/harnesses/kafka-streams-schedule-operations/kstreams.yml $(TEMP_DIR) $(SEQUENCE)
	bash -c "diff --strip-trailing-cr  <(tail -n 1 $(STEPS_DIR)/dev/expected-output.txt) <(tail -n 1 $(DEV_OUTPUTS_DIR)/actual-output.txt)"
	reset
