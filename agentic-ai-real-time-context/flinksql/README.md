<!-- title: Streamhouse for AI Agents Part 3 of 3: Ground a Streaming Agent with the Real-Time Context Engine -->
<!-- description: In this tutorial, learn how to give a Streaming Agent live business context from Kafka using the Real-Time Context Engine and Flink SQL in Confluent Cloud, with step-by-step instructions and supporting code. -->

# Streamhouse for AI Agents Part 3 of 3: Ground a Streaming Agent with the Real-Time Context Engine

In [Part 1](https://developer.confluent.io/confluent-tutorials/agentic-ai-model-tool-setup/flinksql/) and [Part 2](https://developer.confluent.io/confluent-tutorials/agentic-ai-streaming-agent/flinksql/) of this tutorial series, you set up an LLM model and an MCP tool for Linear in Confluent Cloud, and then built a Streaming Agent that uses them to turn customer communications into issues in Linear. The agent can see what is already in Linear, but it knows nothing about the customer who wrote in. A message like _"I would like a partial refund on order 9189"_ becomes an issue that a human then has to enrich by looking up the order and the customer's membership level before deciding how urgent it is.

In this tutorial, you will give the agent that context using the [Real-Time Context Engine](https://docs.confluent.io/cloud/current/ai/real-time-context-engine/overview.html) (RTCE). RTCE materializes Kafka topic data into a low-latency serving layer and exposes it to agents as MCP tools, so the agent can query current data without a Kafka consumer, a separate database, or a Flink job to maintain. You will:

1. Create an `orders` table in Flink SQL that records each order's customer membership level
2. Enable RTCE on the `orders` topic and create credentials for its MCP endpoint
3. Create an MCP tool for RTCE in Flink SQL
4. Alter the Streaming Agent from Part 2 to add the RTCE tool and set the priority of each Linear issue based on the customer's membership level

> **A note on how this tutorial relates to a broader [Streamhouse](https://streamhouse.com/) data architecture:** Streamhouse is an open, vendor-neutral category of data architectures that keep the current state of a business continuously available to production applications, analytics, and AI agents. It describes five stages: capture, transport, transform, govern, and serve. In this series, customer communications and orders are records in Kafka topics (transport), and Flink SQL invokes the agent on each new communication (transform). The data is schematized throughout: Flink registers a schema in Schema Registry for each table's topic, and RTCE requires a registered schema in order to expose a topic, so the agent queries typed, governed columns (govern). RTCE is the serve stage: the current state of each order is queryable by the agent at the moment it handles a communication, with no batch export in between.

## Prerequisites

- Completion of Parts 1 and 2 of this tutorial series with all resources left up and running:
  - [Part 1: Set up an LLM and MCP tool in Confluent Cloud](https://developer.confluent.io/confluent-tutorials/agentic-ai-model-tool-setup/flinksql/)
  - [Part 2: Build a Streaming Agent that uses MCP tools](https://developer.confluent.io/confluent-tutorials/agentic-ai-streaming-agent/flinksql/)
- A Kafka cluster in a [region supported by RTCE](https://docs.confluent.io/cloud/current/ai/real-time-context-engine/limitations.html). RTCE is currently available on AWS only. If you ran the Part 1 quickstart with its default cloud provider, you are already on AWS.
- The Confluent CLI, `jq`, and Node.js (all from Part 1)

## Create the orders table

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

Create an `orders` table. Each order records the customer and their membership level at the time of the order. The primary key, upsert changelog mode, compacted cleanup policy, and raw-bytes key make RTCE keep only the latest record for each order. RTCE's [limitations](https://docs.confluent.io/cloud/current/ai/real-time-context-engine/limitations.html) page describes the key format requirements.

```sql
CREATE TABLE orders (
  order_id INT,
  customer_name STRING,
  membership_level STRING,
  order_total DECIMAL(10, 2),
  order_status STRING,
  PRIMARY KEY (order_id) NOT ENFORCED
) WITH (
  'changelog.mode' = 'upsert',
  'kafka.cleanup-policy' = 'compact'
);
```

Creating the table also creates a Kafka topic named `orders` and registers its value schema in Schema Registry. RTCE requires a registered schema.

Insert a few orders. In a production deployment, these would be produced by an order management system or captured from its database with a CDC connector. Order `9189` is the one from the refund request in Part 2:

```sql
INSERT INTO orders
  VALUES
    (9189, 'Alex Rivera', 'GOLD', 214.50, 'SHIPPED'),
    (9190, 'Jordan Lee', 'PLATINUM', 489.00, 'SHIPPED'),
    (9191, 'Sam Patel', 'STANDARD', 36.25, 'PROCESSING');
```

Confirm that the orders are there:

```sql
SELECT * FROM orders;
```

## Enable RTCE on the orders topic

Exit the Flink SQL shell (`exit;`) and find your cluster's region:

```shell
confluent kafka cluster describe
```

Enable RTCE on the `orders` topic, using the region from the output. The cloud must be `aws`:

```shell
confluent rtce rtce-topic create \
  --cloud aws \
  --region <CLUSTER_REGION> \
  --topic-name orders \
  --description "Customer orders. Each order has the customer's membership_level (PLATINUM, GOLD, or STANDARD)."
```

The description is visible to agents when they discover topics, so describe what the topic contains and what its key columns mean.

Check the status of the RTCE topic:

```shell
confluent rtce rtce-topic describe orders
```

Run the command again every few seconds until the status changes from `PENDING` to `ACTIVE`. Don't continue until it's `ACTIVE`.

RTCE reads the topic's cleanup policy to decide how to materialize it. The `orders` topic uses the `compact` policy and a raw-bytes key, so RTCE materializes it in upsert mode and keeps only the latest record for each `order_id`.

## Create credentials and find the MCP endpoint

RTCE's MCP endpoint authenticates with a Confluent Cloud API key that has global scope. Create one:

```shell
confluent api-key create --resource global --description "RTCE tutorial"
```

Save the API key and secret. You'll provide both when creating a Flink connection in the next step.

Next, get the MCP endpoint URL. In the [Confluent Cloud Console](https://confluent.cloud), navigate to your cluster and click `Topics`. In the `orders` row, find the `Context engine` column, click `Details`, and then click `Copy topic details to clipboard`. The endpoint resembles:

```plaintext
https://mcp.{CLUSTER_REGION}.aws.confluent.cloud/mcp/v1/context-engine/organizations/{ORG_ID}/environments/{ENV_ID}/kafka-clusters/{LKC_ID}
```

## Create the RTCE connection and tool

Start a Flink SQL shell again:

```shell
confluent flink shell --compute-pool \
  $(confluent flink compute-pool list -o json | jq -r ".[0].id")
```

Create a connection to RTCE's MCP server. The Linear connection in Part 1 used a bearer token. RTCE uses HTTP Basic authentication, so provide the API key as the username and the API secret as the password:

```sql
CREATE CONNECTION `rtce-mcp-connection`
  WITH (
    'type' = 'mcp_server',
    'endpoint' = '<RTCE_MCP_ENDPOINT>',
    'transport-type' = 'STREAMABLE_HTTP',
    'username' = '<API_KEY>',
    'password' = '<API_SECRET>'
  );
```

RTCE exposes three MCP tools:

- `listTopics`: lists the topics that have RTCE enabled
- `getMetadata`: returns a topic's schema, including column names, data types, and primary key information
- `queryData`: runs a read-only SQL query against a topic. RTCE supports key lookups, filters, compound predicates, ordering, and row limits, but not aggregates, joins, or subqueries. Queries return at most 200 rows.

Create a tool that allows the agent to call all three:

```sql
CREATE TOOL rtce_tool
USING CONNECTION `rtce-mcp-connection`
WITH (
  'type' = 'mcp',
  'allowed_tools' = 'listTopics,getMetadata,queryData',
  'request_timeout' = '30'
);
```

## Alter the Streaming Agent

Add the RTCE tool to the `chat_listener_agent` from Part 2. The agent keeps its Linear tool and now has both:

```sql
ALTER AGENT chat_listener_agent ADD TOOL rtce_tool;
```

The agent now makes more tool calls per communication: looking up the order, checking Linear for similar issues, and creating the issue. Raise the iteration limit from the Part 2 value of `5` so it has room to finish:

```sql
ALTER AGENT chat_listener_agent SET ('max_iterations' = '10');
```

Next, update the prompt. In addition to the instructions from Part 2, it tells the agent to look up the order in RTCE and to use the customer's membership level to set the Linear issue priority (in Linear, `1` is Urgent, `2` is High, `3` is Medium, and `4` is Low). Naming the topic and the columns up front saves the agent from having to discover them on every run:

```sql
ALTER AGENT chat_listener_agent
SET PROMPT 'Create an issue from the content using <LINEAR_TEAM_ID> as the team ID. Do not create a new issue if a similar issue already exists. If the content mentions an order number, look up that order in the orders topic using the Real-Time Context Engine tool, querying by order_id, and read its membership_level. Use the membership_level to set the issue priority: PLATINUM is priority 1, GOLD is priority 2, and STANDARD is priority 3. If no order is mentioned or the order is not found, use priority 4. Mention the order number and membership level in the issue description.';
```

## Test the agent with order context

Insert two new customer communications that refer to the new orders:

```sql
INSERT INTO customer_communications
  VALUES
    (UUID(), 'Hi, order 9190 arrived with a cracked screen on the display unit. I would like a replacement sent out.'),
    (UUID(), 'Hello, my order 9191 has been processing for days and I would like to know when it will ship.');
```

Run the agent:

```sql
SELECT
  status,
  response
FROM customer_communications,
  LATERAL TABLE(AI_RUN_AGENT('chat_listener_agent', `prompt`, `id`, MAP['debug', true]));
```

This query processes every row in `customer_communications`, including the ones from Part 2. The agent recognizes that issues for those already exist and doesn't create new ones. With `debug` enabled, you can see the agent call the RTCE tool to look up the order.

Head over to your [Linear workspace](https://linear.app/) to see the new issues. Order `9190` belongs to a `PLATINUM` customer, so its issue should have an Urgent priority. Order `9191` belongs to a `STANDARD` customer, so its issue should have a Medium priority. LLM output varies from run to run, so wording and tool-call sequences may differ, but the priorities should follow the membership levels.

## Run the agent continuously

So far you have been running the agent with a `SELECT` query. To run it continuously, so that every new customer communication is handled as it arrives, write the results to a table with an `INSERT INTO` statement:

```sql
CREATE TABLE agent_results (
  id STRING,
  status STRING,
  response STRING
);
```

```sql
INSERT INTO agent_results
SELECT
  id,
  status,
  response
FROM customer_communications,
  LATERAL TABLE(AI_RUN_AGENT('chat_listener_agent', `prompt`, `id`));
```

This statement keeps running. Insert another communication from a second Flink SQL shell and watch a new issue appear in Linear:

```sql
INSERT INTO customer_communications
  VALUES
    (UUID(), 'Hi, I was charged twice for order 9189. Can you please refund the duplicate charge?');
```

## Further experimentation

Try extending the agent:

- **Add more context.** Enable RTCE on other topics, like shipments or customer profiles, and have the agent use `listTopics` and `getMetadata` to discover them.
- **Route issues.** Linear's MCP server accepts parameters like labels and assignees. Prompt the agent to route PLATINUM customers' issues to a specific label or assignee.

## Clean up

When you are done experimenting, delete the `agentic-ai-env` environment to clean up the Confluent Cloud infrastructure created for this tutorial. Run the following command in your terminal to get the environment ID of the form `env-123456` corresponding to the environment named `agentic-ai-env`:

```shell
confluent environment list
```

Delete the environment:

```shell
confluent environment delete <ENVIRONMENT_ID>
```

Also delete the global API key you created for RTCE if you no longer need it:

```shell
confluent api-key delete <API_KEY>
```
