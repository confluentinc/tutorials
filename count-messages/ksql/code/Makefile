STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEST_OUTPUTS_DIR := $(STEPS_DIR)/test/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev, test"

tutorial:
	make tutorial_main || make cleanup

tutorial_main:  
	# Set up the output folders
	rm -r $(DEV_OUTPUTS_DIR) || true
	rm -r $(TEST_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	mkdir -p $(TEST_OUTPUTS_DIR)
	# Run the test harness
	harness-runner ../../../../../_data/harnesses/count-messages/ksql.yml $(TEMP_DIR) $(SEQUENCE)
	# Check the output vs expected
	diff --strip-trailing-cr $(STEPS_DIR)/dev/03d-count-all-messages-pageview.out $(DEV_OUTPUTS_DIR)/03d-count-all-messages-pageview/output-0.log
	bash -c "diff --strip-trailing-cr $(STEPS_DIR)/dev/04a-build-table.out <(tail -n+3 $(DEV_OUTPUTS_DIR)/04a-build-table/output-0.log)"
	diff --strip-trailing-cr $(STEPS_DIR)/dev/04c-pull-query-table.out $(DEV_OUTPUTS_DIR)/04c-pull-query-table/output-0.log
	diff --strip-trailing-cr $(STEPS_DIR)/dev/05a-pull-query-restapi.out $(DEV_OUTPUTS_DIR)/05a-pull-query-restapi.log
	bash -c "diff --strip-trailing-cr $(STEPS_DIR)/test/02-expected-results.out <(grep -v 'OpenJDK 64-Bit Server VM warning' $(TEST_OUTPUTS_DIR)/test-results.log)"

cleanup:
	cd $(TEMP_DIR)
	docker compose down