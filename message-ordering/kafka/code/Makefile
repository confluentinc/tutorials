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
	harness-runner ../../../../../_data/harnesses/message-ordering/kafka.yml $(TEMP_DIR) $(SEQUENCE)
	bash -c "diff -B -I 'Processed' --strip-trailing-cr $(STEPS_DIR)/test/expected-data-from-myTopic-partition-0.sh $(TEST_OUTPUTS_DIR)/actual-output-myTopic-partition-0.txt"
	bash -c "diff -B -I 'Processed' --strip-trailing-cr $(STEPS_DIR)/test/expected-data-from-myTopic-partition-1.sh $(TEST_OUTPUTS_DIR)/actual-output-myTopic-partition-1.txt"
	reset
