<!-- title: Streamhouse for AI Agents Part 2 of 3: Build a Streaming Agent that uses MCP tools -->
<!-- description: In this tutorial, learn how to build and evolve a Streaming Agent that uses MCP tools with Flink SQL in Confluent Cloud, with step-by-step instructions and supporting code. -->

# Streamhouse for AI Agents Part 2 of 3: Build a Streaming Agent that uses MCP tools

In [Part 1](https://developer.confluent.io/confluent-tutorials/agentic-ai-model-tool-setup/flinksql/) of this tutorial series, you created and tested the models and tools required for a customer support operations use case: generating summarized, structured issues in a project management platform (Linear) based on unstructured customer feedback. In this tutorial, you will build on this foundation and create a [Streaming Agent](https://docs.confluent.io/cloud/current/ai/streaming-agents/overview.html) in Confluent Cloud. Then [Part 3](https://developer.confluent.io/confluent-tutorials/agentic-ai-real-time-context/flinksql/) extends the agent with live business context from Kafka, served by the Real-Time Context Engine, so it can enrich each issue with details about the customer and their order.

> **A note on how this tutorial relates to a broader [Streamhouse](https://streamhouse.com/) data architecture:** Streamhouse is an open, vendor-neutral category of data architectures that keep the current state of a business continuously available to production applications, analytics, and AI agents. One of its principles is that data stays current as business events occur, rather than being refreshed through periodic batch processes. The Streaming Agent you build here follows that pattern. Customer communications arrive on a Kafka topic (transport) whose schema is registered in Schema Registry (govern), and Flink SQL invokes the agent on each new record (transform), so the agent acts on events as they occur and no batch job is needed. In Part 3, the Real-Time Context Engine (RTCE) serves current order data to the agent (serve) so that it can use the customer's membership level to decide how each support issue is prioritized.

## Prerequisites

- Completion of Part 1 of this tutorial series with all resources left up and running: [Set up an LLM and MCP tool in Confluent Cloud](https://developer.confluent.io/confluent-tutorials/agentic-ai-model-tool-setup/flinksql/)

## Open the Flink SQL shell

Ensure that the `agentic-ai-env` environment is active:

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
  'allowed_tools' = 'save_issue',
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

Test your agent against sample customer communications. In a production deployment, communications would be produced into Kafka via a connector (e.g., the [HTTP Source V2 Connector](https://docs.confluent.io/cloud/current/connectors/cc-http-source-v2.html) against a communication platform's REST API) or other Kafka client. Here, you create a table and insert sample communications into it.

```sql
CREATE TABLE customer_communications (
  id STRING,
  prompt STRING
);
```

```sql
INSERT INTO `customer_communications`
  VALUES
    (UUID(), 'I cannot log in to the online store. It says the site is undergoing maintenance for an entire day.');
```

Now run the agent against this input. The `response` column shows how the agent handled it, and you can head over to your [Linear workspace](https://linear.app/) to see the issue created.

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

You will see that duplicate issues get created in Linear. This makes sense because the agent is write-only with respect to Linear. To fix that, give the agent the ability to gather more context about your Linear workspace.

First, remember that Linear's MCP server has other tools available beyond issue creation. You can use the `list_issues` tool to let your agent look up existing issues. Run the following statement to allow the MCP tool to both create and list issues:

```sql
ALTER TOOL linear_mcp_tool
SET (
  'allowed_tools' = 'save_issue,list_issues'
);
```

Next, adjust the agent prompt to prevent near-duplicate issues from being created:

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

If you add more complaints about the site undergoing maintenance, you will see that the agent doesn't take action, and the response explains why. For example, `An issue similar to your problem already exists in the team's Linear workspace`.

## Test with a new issue

To verify that the agent handles a completely new issue correctly, insert another communication, this time requesting a refund due to a shipping mistake:

```sql
INSERT INTO customer_communications
  VALUES
    (UUID(), 'Hi, I would like a partial refund on order 9189 because one of the items (the bike tire) was not included. You can just credit my account balance, please.');
```

Run the agent again:

```sql
SELECT
  status,
  response
FROM customer_communications,
  LATERAL TABLE(AI_RUN_AGENT('chat_listener_agent', `prompt`, `id`, MAP['debug', true]));
```

The agent skips the earlier maintenance complaints because issues for them already exist, and creates a new issue for the refund request. Head over to your [Linear workspace](https://linear.app/) to see it:

![Linear refund issue](https://raw.githubusercontent.com/confluentinc/tutorials/master/agentic-ai-streaming-agent/flinksql/img/linear_refund_issue.png)

## Further experimentation

How can you make your agent even smarter? Try implementing enhancements such as:

- Adjust the agent prompt to set issue priority more intelligently
- Give the agent the ability to handle multiple communications about the same issue and capture them all in the same issue. Hint: Linear's MCP server provides `update_issue` and `create_comment` tools.

## Next steps

In [Part 3](https://developer.confluent.io/confluent-tutorials/agentic-ai-real-time-context/flinksql/), give your agent live business context from Kafka using the Real-Time Context Engine, and then run the agent continuously.

## Clean up

If you aren't continuing to Part 3, delete the `agentic-ai-env` environment to clean up the Confluent Cloud infrastructure created for this tutorial. Run the following command in your terminal to get the environment ID of the form `env-123456` corresponding to the environment named `agentic-ai-env`:

```shell
confluent environment list
```

Delete the environment:

```shell
confluent environment delete <ENVIRONMENT_ID>
```
