STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEST_OUTPUTS_DIR := $(STEPS_DIR)/test/outputs
PROD_OUTPUTS_DIR := $(STEPS_DIR)/prod/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test, prod, ccloud"

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	rm -r $(TEST_OUTPUTS_DIR) || true
	mkdir -p $(DEV_OUTPUTS_DIR)
	mkdir -p $(TEST_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/time-concepts/ksql.yml $(TEMP_DIR) $(SEQUENCE)
	$(eval producer_eventTime=$(shell grep "Record written" $(DEV_OUTPUTS_DIR)/producer-output.log | awk -F"[, ]" 'NR==1{print $$8;}'))
	$(eval producer_logTime=$(shell grep "Record written" $(DEV_OUTPUTS_DIR)/producer-output.log | awk -F"[, ]" 'NR==1{print $$12;}'))
	$(eval ksqlDB_logTimeQ_eventTime=$(shell grep 100 $(DEV_OUTPUTS_DIR)/transient-query-logtime/output-0.log | awk 'NR==1{print substr($$2,2);}')) 
	$(eval ksqlDB_logTimeQ_rowTime=$(shell grep 100 $(DEV_OUTPUTS_DIR)/transient-query-logtime/output-0.log | awk 'NR==1{print substr($$3,2);}')) 
	$(eval ksqlDB_eventTimeQ_eventTime=$(shell grep 100 $(DEV_OUTPUTS_DIR)/transient-query-eventtime/output-0.log | awk 'NR==1{print substr($$2,2);}')) 
	$(eval ksqlDB_eventTimeQ_rowTime=$(shell grep 100 $(DEV_OUTPUTS_DIR)/transient-query-eventtime/output-0.log | awk 'NR==1{print substr($$3,2);}')) 
	test "$(producer_eventTime)" = "$(ksqlDB_logTimeQ_eventTime)"
	test "$(producer_logTime)"   = "$(ksqlDB_logTimeQ_rowTime)"
	test "$(producer_eventTime)" = "$(ksqlDB_eventTimeQ_eventTime)"
	test "$(producer_eventTime)" = "$(ksqlDB_eventTimeQ_rowTime)"
	reset
