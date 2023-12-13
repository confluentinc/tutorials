STEPS_DIR := tutorial-steps
DEV_OUTPUTS_DIR := $(STEPS_DIR)/dev/outputs
TEMP_DIR := $(shell mktemp -d)

tutorial:
	rm -r $(DEV_OUTPUTS_DIR) || true
	mkdir $(DEV_OUTPUTS_DIR)
	harness-runner ../../../../../_data/harnesses/generate-test-data-streams/kafka.yml $(TEMP_DIR)

	# ---
	# Since it's a random data generator it's tricky to assert what we should match here
	# We check for column headings, and we check for a rowcount match.
	# Two quirks to note here: 
	# 1. The $ needs to be $$ because of how make parses it
	# 2. The resulting $1 that's used in awk needs to be escaped 
	#     to \$1 because https://unix.stackexchange.com/questions/577491/script-not-working-when-passed-to-bash-via-c/577492#577492
	bash -c "diff <(wc -l $(STEPS_DIR)/dev/consume-topic-01_expected.log|awk '{print \$$1}')  <(wc -l $(DEV_OUTPUTS_DIR)/consume-topic-01.out|awk '{print \$$1}')"
	bash -c "diff <(wc -l $(STEPS_DIR)/dev/consume-topic-02a_expected.log|awk '{print \$$1}') <(wc -l $(DEV_OUTPUTS_DIR)/consume-topic-02a.out|awk '{print \$$1}')"
	bash -c "diff <(wc -l $(STEPS_DIR)/dev/consume-topic-02b_expected.log|awk '{print \$$1}') <(wc -l $(DEV_OUTPUTS_DIR)/consume-topic-02b.out|awk '{print \$$1}')"

	# ---
	diff --ignore-all-space --strip-trailing-cr $(STEPS_DIR)/dev/check-plugin_expected.log $(DEV_OUTPUTS_DIR)/check-plugin.out
	diff --ignore-all-space --strip-trailing-cr $(STEPS_DIR)/dev/check-connector-01_expected.log $(DEV_OUTPUTS_DIR)/check-connector-01.out
	diff --ignore-all-space --strip-trailing-cr $(STEPS_DIR)/dev/check-connector-02_expected.log $(DEV_OUTPUTS_DIR)/check-connector-02.out
	bash -c "diff --ignore-all-space --strip-trailing-cr <(grep -v 'Date:' $(STEPS_DIR)/dev/create-connector-01_expected.log) <(grep -v 'Date:' $(DEV_OUTPUTS_DIR)/create-connector-01.out)"
	bash -c "diff --ignore-all-space --strip-trailing-cr <(grep -v 'Date:' $(STEPS_DIR)/dev/create-connector-02_expected.log) <(grep -v 'Date:' $(DEV_OUTPUTS_DIR)/create-connector-02.out)"

	# ----
	# In this test we strip out everything except for the confirmation message "<service> is ready". 
	# We then count the number of lines for the diff because the ✅ emoji causes some confusion in the diff
	bash -c "diff <(grep 'is ready' $(STEPS_DIR)/dev/wait-for-containers_expected.log|wc -l) <(grep 'is ready' $(DEV_OUTPUTS_DIR)/wait-for-containers.out|wc -l)"

