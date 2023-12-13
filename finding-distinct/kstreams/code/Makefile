STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"

clean:
	rm -r $(DEV_OUTPUTS_DIR) || true

create-outputs-dir:
	mkdir $(DEV_OUTPUTS_DIR)

run-harness:
	harness-runner ../../../../../_data/harnesses/finding-distinct/kstreams.yml $(TEMP_DIR) $(SEQUENCE) 

diff:
	diff --strip-trailing-cr $(STEPS_DIR)/dev/expected-output-events.json $(DEV_OUTPUTS_DIR)/actual-output-events.json

tutorial:
	make --no-print-directory clean
	make --no-print-directory create-outputs-dir
	make --no-print-directory run-harness
	make --no-print-directory diff
	reset
