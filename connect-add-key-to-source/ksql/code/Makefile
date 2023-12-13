STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/connect-add-key-to-source/ksql.yml $(TEMP_DIR) $(SEQUENCE)
	diff --strip-trailing-cr $(STEPS_DIR)/dev/query-postgres_expected.log $(DEV_OUTPUTS_DIR)/query-postgres.log
	# tr is used to replace - with spaces from the ksql CLI output, because the file being compared to is what is rendered in the tutorial
	bash -c "diff --ignore-all-space --strip-trailing-cr <(cat $(STEPS_DIR)/dev/create-connector_expected.log|tr '-' ' ') $(DEV_OUTPUTS_DIR)/create-connector/output-0.log"
	bash -c "diff --ignore-all-space --strip-trailing-cr <(cat $(STEPS_DIR)/dev/check-connector_expected.log|tr '-' ' ') $(DEV_OUTPUTS_DIR)/check-connector/output-0.log"
	bash -c "diff --ignore-all-space --strip-trailing-cr <(cat $(STEPS_DIR)/dev/describe-connector_expected.log|tr '-' ' ') $(DEV_OUTPUTS_DIR)/describe-connector/output-0.log"
	bash -c "diff --ignore-all-space --strip-trailing-cr <(cat $(STEPS_DIR)/dev/create-table_expected.log|tr '-' ' ') $(DEV_OUTPUTS_DIR)/create-table/output-0.log"
	bash -c "diff --ignore-all-space --strip-trailing-cr $(STEPS_DIR)/dev/query-table_expected.log $(DEV_OUTPUTS_DIR)/query-table/output-1.log"
	bash -c "diff --strip-trailing-cr <(sed 's/^.*key/key/' $(STEPS_DIR)/dev/consume-topic_expected.log) <(sed 's/^.*key/key/' $(DEV_OUTPUTS_DIR)/consume-topic/output-0.log)"
	reset
