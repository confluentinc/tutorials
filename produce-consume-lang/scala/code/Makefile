STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/prod/outputs
TEMP_DIR := $(shell mktemp -d)

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/produce-consume-lang/scala.yml $(TEMP_DIR)
	bash -c 'diff --strip-trailing-cr <(cat $(STEPS_DIR)/prod/expected-result.json) <(cat $(DEV_OUTPUTS_DIR)/actual-result.json)'