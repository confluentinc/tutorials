STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/streams-to-table/kstreams.yml $(TEMP_DIR)
	bash -c "diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-streams-output.txt  <(head -n 6 $(DEV_OUTPUTS_DIR)/actual-streams-output.txt)"
	bash -c "diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-table-output.txt  <(head -n 2 $(DEV_OUTPUTS_DIR)/actual-table-output.txt)"
	reset

