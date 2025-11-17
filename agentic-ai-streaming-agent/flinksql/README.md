<!-- title: How to build and evolve a Streaming Agent that uses MCP tools with Flink SQL in Confluent Cloud -->
<!-- description: In this tutorial, learn how to build and evolve a Streaming Agent that uses MCP tools with Flink SQL in Confluent Cloud, with step-by-step instructions and supporting code. -->

# Agentic AI Part 2 of 2: Building and evolving a Streaming Agent that uses MCP tools

In [Part 1](https://developer.confluent.io/confluent-tutorials/agentic-ai-model-tool-setup/flinksql/) of this tutorial series, you created and tested the models and tools required for a customer support operations use case: generating summarized, structured issues in a project management platform (Linear) based on unstructured customer feedback. In this tutorial, we will build on this foundation and create a [Streaming Agent](https://docs.confluent.io/cloud/current/ai/streaming-agents/overview.html) in Confluent Cloud.

## Prerequisites

- Completion of Part 1 of this tutorial series with all resources left up and running: [LLM model and MCP tool setup in Confluent Cloud](https://developer.confluent.io/confluent-tutorials/agentic-ai-model-tool-setup/flinksql/)

## Open the Flink SQL shell

Ensure that the `agentic-ai-env` environment is active when you run:

```shell
confluent environment list
```

If it isn't, set it to be the active environment:

```shell
confluent environment use <ENVIRONMENT_ID>
```

Start a Flink SQL shell:

```shell
confluent flink shell --compute-pool \
  $(confluent flink compute-pool list -o json | jq -r ".[0].id")
```

## Create a tool resource for the Streaming Agent

In the Flink SQL shell, create a tool to access Linear's MCP server:

```sql
CREATE TOOL linear_mcp_tool
USING CONNECTION `linear-mcp-connection`
WITH (
  'type' = 'mcp',
  'allowed_tools' = 'create_issue',
  'request_timeout' = '30'
);
```

## Create the Streaming Agent

Next, create a Streaming Agent using the tool you just created as well as the model you created earlier. Since the agent won't know your Linear team ID, provide it as a prompt hint:

```sql
CREATE AGENT chat_listener_agent
USING MODEL chat_listener
USING PROMPT 'Create an issue from the content using <LINEAR_TEAM_ID> as the team ID'
USING TOOLS linear_mcp_tool
WITH (
    'max_iterations' = '5'
  );
```

## Test the Streaming Agent

Let's test your agent against sample customer communications. In a production deployment, communications would be produced into Kafka via a connector (e.g., the [HTTP Source V2 Connector](https://docs.confluent.io/cloud/current/connectors/cc-http-source-v2.html) against a communication platform's REST API) or other Kafka client. Here, we create a table and insert sample communications into it.

```sql
CREATE TABLE customer_communications (
  id STRING,
  prompt STRING);
```

```sql
INSERT INTO `customer_communications`
  VALUES
    (UUID(), 'I cannot log in to the online store. It says the site is undergoing maintenance for an entire day.');
```

Now run the agent against this input. You will see how the agent handled it in the `response`, and you can head over to your [Linear workspace](https://linear.app/) to see the issue created.

```sql
SELECT
  status,
  response
FROM customer_communications,
  LATERAL TABLE(AI_RUN_AGENT('chat_listener_agent', `prompt`, `id`, MAP['debug', true]));
```

## Evolve the Streaming Agent

You are off to a good start with your Streaming Agent, but you may already see room for improvement. For example, if you insert another complaint about the site undergoing maintenance:

```sql
INSERT INTO customer_communications
  VALUES
    (UUID(), 'Hi, I am not able to log in due to the site being under maintenance. The ETA for the maintenance to be completed was last night, so it seems like something might be wrong.');
```

Then run the agent query again:

```sql
SELECT
  status,
  response
FROM customer_communications,
  LATERAL TABLE(AI_RUN_AGENT('chat_listener_agent', `prompt`, `id`, MAP['debug', true]));
```

You will see that duplicate issues get created in Linear. This makes sense because the agent is write-only with respect to Linear. Let's fix that and give the agent the ability to gather more context about your Linear workspace.

First, remember that Linear's MCP server has other tools available beyond issue creation. We can use the `list_issues` tool to give our agent the ability to gather more context about existing issues. Run the following statement to allow the MCP tool to both create and list issues.

```sql
ALTER TOOL linear_mcp_tool
SET (
  'allowed_tools' = 'create_issue,list_issues'
);
```

Next, adjust the model prompt to prevent near-duplicate issues from being created.

```sql
ALTER AGENT chat_listener_agent
SET PROMPT 'Create an issue from the content using <LINEAR_TEAM_ID> as the team ID. Do not create a new issue if a similar issue already exists.';
```

Run the agent again:

```sql
SELECT
  status,
  response
FROM customer_communications,
  LATERAL TABLE(AI_RUN_AGENT('chat_listener_agent', `prompt`, `id`, MAP['debug', true]));
```

If you add more complaints about the site undergoing maintenance, you will see that the agent doesn't take action. You will see reasons explaining why; for example, `An issue similar to your problem already exists in the team's Linear workspace`.

## Run the Streaming Agent continuously

Once you are satisfied with an agent's quality, you can run it continuously by wrapping it as a [`CREATE TABLE AS SELECT`](https://docs.confluent.io/cloud/current/flink/reference/statements/create-table.html#create-table-as-select-ctas) statement:

```sql
CREATE TABLE chat_listener_agent_output AS
SELECT
  status,
  response
FROM customer_communications,
  LATERAL TABLE(AI_RUN_AGENT('chat_listener_agent', `prompt`, `id`, MAP['debug', true]));
```

To test that it's working, insert another value into the customer communications stream, this time for a completely new issue requesting a refund due to a shipping mistake:

```sql
INSERT INTO customer_communications
  VALUES
    (UUID(), 'Hi, I would like a partial refund on order 9189 because one of the items (the bike tire) was not included. you can just credit my account balance please.');
```

Since the Streaming Agent is running as a CTAS statement, you can see how the agent handles the input by querying the `chat_listener_agent_output` table, or head over to your [Linear workspace](https://linear.app/), where you will see a new issue created:

![Linear refund issue](https://raw.githubusercontent.com/confluentinc/tutorials/master/agentic-ai-streaming-agent/flinksql/img/linear_refund_issue.png)

## Further experimentation

How can you make your agent even smarter? Try implementing enhancements such as:

- Adjust the model prompt to set issue priority more intelligently
- Give the agent the ability to handle multiple communications about the same issue and capture them all in the same issue. Hint: Linear's MCP server provides `update_issue` and `create_comment` tools.

## Clean up

Once you are done experimenting, delete the `agentic-ai-env` environment in order to clean up the Confluent Cloud infrastructure created for this tutorial. Run the following command in your terminal to get the environment ID of the form `env-123456` corresponding to the environment named `agentic-ai-env`:

```shell
confluent environment list
```

Delete the environment:

```shell
confluent environment delete <ENVIRONMENT_ID>
```
