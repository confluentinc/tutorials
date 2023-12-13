STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEST_OUTPUTS_DIR := $(STEPS_DIR)/test/outputs
PROD_OUTPUTS_DIR := $(STEPS_DIR)/prod/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	rm -r $(TEST_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	mkdir -p $(TEST_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/rekeying/ksql.yml $(TEMP_DIR) $(SEQUENCE)
	bash -c "diff --strip-trailing-cr <(cut -d ',' -f 2-5 $(STEPS_DIR)/dev/expected-print-input.log|sort) <(cut -d ',' -f 2-5 $(DEV_OUTPUTS_DIR)/print-input-topic/output-0.log|sort)"
	diff --strip-trailing-cr --ignore-space-change $(STEPS_DIR)/dev/expected-describe.log $(DEV_OUTPUTS_DIR)/describe-output/output-0.log
	# Unless ksqlDB adds support for SLEEP, or we split the docker_ksql_cli_session and insert a bash sleep step (a lot of heavy lifting), this option will hopefully suffice for now. It simply greps out the offending KAFKA_STRING mention in the test at the end.
	# See https://github.com/confluentinc/kafka-tutorials/issues/510
	bash -c "diff --strip-trailing-cr <(cut -d ',' -f 2-5 $(STEPS_DIR)/dev/expected-print-output.log|sort) <(cut -d ',' -f 2-5 $(DEV_OUTPUTS_DIR)/print-output-topic/output-0.log|sort|grep -v KAFKA_STRING)"	
	reset
