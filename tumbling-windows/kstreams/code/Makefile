STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/tumbling-windows/kstreams.yml $(TEMP_DIR) $(SEQUENCE)
	diff --ignore-blank-lines --strip-trailing-cr $(STEPS_DIR)/dev/windowed-counted-ratings.txt $(DEV_OUTPUTS_DIR)/windowed-ratings-actual.txt
	reset
