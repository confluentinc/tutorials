<!-- title: Streamhouse for AI Agents Part 1 of 3: Set up an LLM and MCP tool in Confluent Cloud -->
<!-- description: In this tutorial, learn how to set up models and MCP tools to be used in agentic AI workflows with Flink SQL in Confluent Cloud, with step-by-step instructions and supporting code. -->

# Streamhouse for AI Agents Part 1 of 3: Set up an LLM and MCP tool in Confluent Cloud

In Part 1 of this tutorial series, you will set up and test the infrastructure and third-party dependencies required for an agentic AI use case: a listener that creates concise tasks in a project management platform based on customer communications. This is a prime example of integrating tools in a model: LLMs are strong at summarizing a customer's natural language, but they lack awareness of your organization's project management platform and the context and connectivity needed to integrate with such an external system. You will use [Amazon Bedrock](https://aws.amazon.com/bedrock/) as the [model provider](https://docs.confluent.io/cloud/current/ai/ai-model-inference.html) in Confluent Cloud and [Linear](https://linear.app/) as the SaaS project management platform (the _tool_ for the model to call). Linear is similar to [Jira](https://www.atlassian.com/software/jira) or [Asana](https://asana.com/).

Automatic ticket creation is a good fit for an automated AI pipeline because the cost of a wrong answer is low. For example, if an agent mistakenly creates a ticket or incorrectly routes one, the humans in the loop can clean it up after the fact.

In [Part 2](https://developer.confluent.io/confluent-tutorials/agentic-ai-streaming-agent/flinksql/), you will build and evolve a Streaming Agent for this use case. In [Part 3](https://developer.confluent.io/confluent-tutorials/agentic-ai-real-time-context/flinksql/) you will ground the agent with live business context from Kafka using the Real-Time Context Engine.

> **A note on how this tutorial relates to a broader [Streamhouse](https://streamhouse.com/) data architecture:** Streamhouse is an open, vendor-neutral category of data architectures that keep the current state of a business continuously available to production applications, analytics, and AI agents. It describes five stages: capture, transport, transform, govern, and serve. This series covers the part of that picture where an agent acts on business events as they happen instead of on data refreshed in batches. In this part, you set up the model and the tool that Flink SQL will call for each event. In Parts 2 and 3, customer communications and orders are records in Kafka topics (transport) with schemas registered in Schema Registry (govern), and Flink SQL invokes the agent on each new communication (transform). In Part 3, the Real-Time Context Engine (RTCE) serves current order data to the agent (serve) so that it can use the customer's membership level to decide how each support issue is prioritized.

## Prerequisites

- A [Confluent Cloud](https://confluent.cloud/signup) account
- The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
- [Node.js and npm](https://docs.npmjs.com/downloading-and-installing-node-js-and-npm) to inspect Linear's MCP server
- [`jq`](https://jqlang.org/download/) for parsing JSON on the command line

## Create Confluent Cloud resources

Log in to your Confluent Cloud account:

```shell
confluent login --prompt --save
```

Install a CLI plugin that streamlines resource creation in Confluent Cloud:

```shell
confluent plugin install confluent-quickstart
```

Run the plugin to create the Confluent Cloud resources (Kafka cluster and Flink compute pool) needed for this tutorial. Note that you may specify a different cloud provider (`gcp` or `azure`) or region. You can find supported regions in a given cloud provider by running `confluent kafka region list --cloud <CLOUD>`. The plugin should complete in under a minute.

```shell
confluent quickstart \
  --environment-name agentic-ai-env \
  --kafka-cluster-name agentic-ai-cluster \
  --compute-pool-name agentic-ai-pool
```

Because this tutorial uses the plugin only to create infrastructure and not to generate client configuration files, you will see the following output when it completes:

```plaintext
No config files were created (no resources were created)
Quickstart complete. Exiting.
```

## Set up Amazon Bedrock model access and AWS credentials

[Sign up for an AWS account](https://portal.aws.amazon.com/billing/signup) if you don't already have one.

This tutorial calls the Claude Sonnet 5 model on Amazon Bedrock in the `us-east-1` region, which is billed through AWS Marketplace based on token usage. Open the [Bedrock console](https://console.aws.amazon.com/bedrock/home?region=us-east-1#/model-catalog), click `Model catalog` in the left-hand navigation, and ensure you have access to `Claude Sonnet 5` from Anthropic.

You will need an AWS access key in order to call the model from Confluent Cloud. Create an IAM user (or use an existing one) in the [IAM console](https://console.aws.amazon.com/iam/home#/users) and attach an inline policy granting the `bedrock:InvokeModel` action:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": "bedrock:InvokeModel",
      "Resource": "*"
    }
  ]
}
```

Then, from that user's `Security credentials` tab, click `Create access key` and choose `Third-party service` as the use case. Save the access key ID and secret access key because you will need them later when creating a remote model in Flink.

If your organization provisions AWS access via SSO (AWS IAM Identity Center) and blocks `iam:CreateUser` with a service control policy, you won't be able to create an IAM user. In that case, use the temporary credentials from your existing SSO session instead, as long as the role you assume has `bedrock:InvokeModel` permission:

```shell
aws sso login --profile <your-profile>
aws configure export-credentials --profile <your-profile>
```

This prints JSON containing `AccessKeyId`, `SecretAccessKey`, and `SessionToken`. Save all three. You'll pass the session token as `aws-session-token` when creating the connection in the next step. Note that these credentials are short-lived (typically 1 to 12 hours, depending on your organization's session duration policy). Once they expire, calls through the connection will start failing, and you'll need to repeat these two commands and re-create the connection with fresh values.

## Set up Linear account and create credentials

[Sign up](https://linear.app/signup) for a free Linear account (no credit card required). As part of the signup flow, you will be prompted to create a workspace. Give your workspace a unique name and select the region that makes sense for you. Click through the quick start prompts until you get to your workspace home page.

You will need a Linear API key in order to call Linear as an MCP-based tool from Confluent Cloud. To create a key, click the workspace dropdown at the top left, then `Settings`. Select `Security & access` in the left-hand navigation, followed by `New API key` under `Personal API keys`. Give the key a name. Under `Permissions`, select `Only select permissions...` and then only check the boxes for `Read` and `Write`. Click `Create`. Save this API key.

## Create Bedrock and Linear connections

To create a remote model or MCP tool in Confluent Cloud, you must provide a [connection](https://docs.confluent.io/cloud/current/flink/reference/statements/create-connection.html) to an external service as a parameter. In this step, you create the prerequisite connections.

To create connections to Amazon Bedrock and Linear, start a Flink SQL shell:

```shell
confluent flink shell --compute-pool \
  $(confluent flink compute-pool list -o json | jq -r ".[0].id")
```

Paste your AWS access key ID and secret access key into the following statement to create a connection to Amazon Bedrock's [Invoke Model API](https://docs.aws.amazon.com/bedrock/latest/APIReference/API_runtime_InvokeModel.html). The endpoint targets the `us.anthropic.claude-sonnet-5` inference profile, which is required for on-demand invocation of Claude Sonnet 5 from `us-east-1`:

```sql
CREATE CONNECTION `bedrock-connection`
  WITH (
    'type' = 'bedrock',
    'endpoint' = 'https://bedrock-runtime.us-east-1.amazonaws.com/model/us.anthropic.claude-sonnet-5/invoke',
    'aws-access-key' = '<AWS_ACCESS_KEY_ID>',
    'aws-secret-key' = '<AWS_SECRET_ACCESS_KEY>'
  );
```

If you're using temporary credentials from an SSO session instead of an IAM user's access key, also include the session token you saved earlier:

```sql
CREATE CONNECTION `bedrock-connection`
  WITH (
    'type' = 'bedrock',
    'endpoint' = 'https://bedrock-runtime.us-east-1.amazonaws.com/model/us.anthropic.claude-sonnet-5/invoke',
    'aws-access-key' = '<AWS_ACCESS_KEY_ID>',
    'aws-secret-key' = '<AWS_SECRET_ACCESS_KEY>',
    'aws-session-token' = '<AWS_SESSION_TOKEN>'
  );
```

The connection is successfully created when you see the following output:

```plaintext
Finished statement execution. Statement phase: COMPLETED.
The server returned empty rows for this statement.
```

Next, create a connection to Linear's MCP server. Linear offers both streamable HTTP and SSE-based [MCP endpoints](https://linear.app/docs/mcp#general). Paste your Linear API key into the following statement to create a connection to Linear's streamable HTTP MCP server:

```sql
CREATE CONNECTION `linear-mcp-connection`
  WITH (
    'type' = 'MCP_SERVER',
    'endpoint' = 'https://mcp.linear.app/mcp',
    'transport-type' = 'streamable_http',
    'token' = '<LINEAR_API_KEY>'
  );
```

Validate that both connections have been created:

```sql
SHOW CONNECTIONS;
```

You will see:

```plaintext
+-----------------------+
|    Connection Name    |
+-----------------------+
| bedrock-connection    |
| linear-mcp-connection |
+-----------------------+
```

## Inspect the Linear MCP server

In this section you will inspect Linear's MCP server to see which tools are available and what parameters they require. You'll need to know this information when invoking the tool in a later step.

Run the following command from your terminal (not the Flink shell) to start an MCP server inspection utility:

```shell
npx @modelcontextprotocol/inspector@latest
```

Click the `Add Servers` button. Enter `linear-mcp` as the `Server ID`, select `streamable-http` as the `Transport`, and enter `https://mcp.linear.app/mcp` as the `URL`. Then click `Add`.

![MCP inspector add server](https://raw.githubusercontent.com/confluentinc/tutorials/master/agentic-ai-model-tool-setup/flinksql/img/mcp-inspector-add-server.png)

Toggle the radio button where it says `Disconnected` to connect to the server.

![MCP inspector connect](https://raw.githubusercontent.com/confluentinc/tutorials/master/agentic-ai-model-tool-setup/flinksql/img/mcp-inspector-connect.png)

Once you're connected, click `Tools` at the top of the page. These are the tools available for building an agentic AI workflow. This tutorial focuses on issue creation, so click the `save_issue` tool to see the fields required to create an issue.

![MCP inspector save_issue](https://raw.githubusercontent.com/confluentinc/tutorials/master/agentic-ai-model-tool-setup/flinksql/img/mcp-inspector-save-issue.png)

You'll notice a required `team` field that you must provide when calling the tool. You can get your team ID (a GUID) by running the following command from your terminal. Be sure to substitute your Linear API key:

```shell
curl \
  -X POST \
  -H "Content-Type: application/json" \
  -H "Authorization: <LINEAR_API_KEY>" \
  --data '{
    "query": "query Teams { teams { nodes { id name } }}"
  }' \
  https://api.linear.app/graphql | jq -r ".data.teams.nodes[0].id"
```

## Create models

In the Flink SQL shell, create a model using the Bedrock connection created earlier. Anthropic models on Bedrock require `max_tokens` to be set explicitly, which you provide via the `bedrock.params.max_tokens` option:

```sql
CREATE MODEL chat_listener
INPUT(prompt STRING)
OUTPUT(response STRING)
WITH (
  'provider' = 'bedrock',
  'task' = 'text_generation',
  'bedrock.connection' = 'bedrock-connection',
  'bedrock.params.max_tokens' = '1024'
);
```

Next, create a similar LLM model, but this time also provide the MCP server connection. You will use this model to invoke Linear's `save_issue` tool in the next step. The `bedrock.system_prompt` option nudges the model to prefer calling a tool over responding directly:

```sql
CREATE MODEL linear_mcp_model
INPUT(prompt STRING)
OUTPUT(response STRING)
WITH (
  'provider' = 'bedrock',
  'task' = 'text_generation',
  'bedrock.connection' = 'bedrock-connection',
  'bedrock.params.max_tokens' = '1024',
  'bedrock.system_prompt' = 'Use the best tool to respond to the input prompt',
  'mcp.connection' = 'linear-mcp-connection'
);
```

Validate that both models have been created:

```sql
SHOW MODELS;
```

You will see:

```plaintext
+------------------+
|    Model Name    |
+------------------+
| chat_listener    |
| linear_mcp_model |
+------------------+
```

## Test models and tool invocation

First, test the base LLM that doesn't call any tools:

```sql
SELECT
  prompt,
  response
FROM
  (SELECT 'What is a good family friendly dog breed? Answer concisely with only the most recommended breed.' AS prompt) t,
LATERAL TABLE(AI_COMPLETE('chat_listener', prompt)) AS r(response);
```

You should see output like the following. It may take 10 to 15 seconds to appear in the query results. Your output may be different because the underlying model is nondeterministic.

```plaintext
prompt                                           response
what is a good family friendly dog breed? ...    Labrador Retriever
```

Next, test MCP tool invocation with the following command. Substitute your Linear team ID.

```sql
SELECT
  AI_TOOL_INVOKE(
    'linear_mcp_model',
    'Create an issue from the following text using <LINEAR_TEAM_ID> as the team ID. I can''t log in to the online store. It says that my account has been locked out. When I try the forgot password route, I don''t get an email to reset it. Please help!',
    MAP[],
    MAP['save_issue', 'Save issue'],
    MAP[]
  ) AS response;
```

You should see a JSON response indicating the status as well as the action taken. In the [Linear web app](https://linear.app/), click `All issues` and you will see a new ticket in the backlog summarizing the issue:

![Linear new issue](https://raw.githubusercontent.com/confluentinc/tutorials/master/agentic-ai-model-tool-setup/flinksql/img/linear_new_issue.png)

## Next steps

Now that you have created models and a tool and verified that they work as expected, proceed to [Part 2](https://developer.confluent.io/confluent-tutorials/agentic-ai-streaming-agent/flinksql/) of this tutorial series.

## Clean up

If you aren't continuing to Part 2, delete the `agentic-ai-env` environment to clean up the Confluent Cloud infrastructure created for this tutorial. Run the following command in your terminal to get the environment ID of the form `env-123456` corresponding to the environment named `agentic-ai-env`:

```shell
confluent environment list
```

Delete the environment:

```shell
confluent environment delete <ENVIRONMENT_ID>
```
