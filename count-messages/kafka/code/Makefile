STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)
SEQUENCE := "dev"

tutorial:
	make tutorial_main || make cleanup

tutorial_main:  
	# # Set up the output folders
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	# # Run the test harness
	harness-runner ../../../../../_data/harnesses/count-messages/kafka.yml $(TEMP_DIR) $(SEQUENCE)
	#
	# 👀 Check test results
	# Check the output vs expected
	diff --strip-trailing-cr $(STEPS_DIR)/dev/03a-count-messages.out $(DEV_OUTPUTS_DIR)/03a-count-messages.log

cleanup:
	cd $(TEMP_DIR)
	docker compose down