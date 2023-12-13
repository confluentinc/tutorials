STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/connect-add-key-to-source/kafka.yml $(TEMP_DIR) $(SEQUENCE)
	diff --strip-trailing-cr $(STEPS_DIR)/dev/query-postgres_expected.log $(DEV_OUTPUTS_DIR)/query-postgres.log
	bash -c "diff --strip-trailing-cr <(head -n1 $(STEPS_DIR)/dev/create-connector_expected.log) <(head -n1 $(DEV_OUTPUTS_DIR)/create-connector.log)"
	diff --strip-trailing-cr $(STEPS_DIR)/dev/check-connector_expected.log $(DEV_OUTPUTS_DIR)/check-connector.log
	diff --strip-trailing-cr --ignore-tab-expansion $(STEPS_DIR)/dev/consume-topic_expected.log $(DEV_OUTPUTS_DIR)/consume-topic.log



	reset
