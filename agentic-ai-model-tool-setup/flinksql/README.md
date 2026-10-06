<!-- title: Agentic AI Part 1 of 2: LLM model and MCP tool setup in Confluent Cloud -->
<!-- description: In this tutorial, learn how to set up models and MCP tools to be used in agentic AI workflows with Flink SQL in Confluent Cloud, with step-by-step instructions and supporting code. -->

# Agentic AI Part 1 of 2: LLM model and MCP tool setup in Confluent Cloud

In Part 1 of this tutorial series, you will set up and test the infrastructure and third-party dependencies required for an agentic AI use case: a listener that creates concise tasks in a project management platform based on customer communications. This is a prime example of integrating tools in a model: LLMs are strong at summarizing a customer's natural language, but they lack awareness of your organization's project management platform and the context and connectivity needed to integrate with such an external system. You will use [Amazon Bedrock](https://aws.amazon.com/bedrock/) as the [model provider](https://docs.confluent.io/cloud/current/ai/ai-model-inference.html) in Confluent Cloud and [Linear](https://linear.app/) as the SaaS project management platform (the _tool_ for the model to call). Linear is similar to [Jira](https://www.atlassian.com/software/jira) or [Asana](https://asana.com/).

After you finish this tutorial, in [Part 2](https://developer.confluent.io/confluent-tutorials/agentic-ai-streaming-agent/flinksql/) of the series you will continue to build and evolve a Streaming Agent for this use case.

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

Install a CLI plugin that streamlines resource creation in Confluent Cloud.

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

Since you are running the plugin to create infrastructure but not generate any client configuration files, you will see final output like the following once it successfully completes:

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

If your organization provisions AWS access via SSO (AWS IAM Identity Center) and blocks `iam:CreateUser` with a service control policy, you won't be able to create an IAM user. In that case, use the temporary credentials from your existing SSO session instead — as long as the role you assume has `bedrock:InvokeModel` permission:

```shell
aws sso login --profile <your-profile>
aws configure export-credentials --profile <your-profile>
```

This prints JSON containing `AccessKeyId`, `SecretAccessKey`, and `SessionToken`. Save all three; you'll pass the session token as `aws-session-token` when creating the connection in the next step. Note that these credentials are short-lived (typically 1-12 hours, depending on your org's session duration policy) — once they expire, calls through the connection will start failing, and you'll need to repeat these two commands and re-create the connection with fresh values.

## Set up Linear account and create credentials

[Sign up](https://linear.app/signup) for a free Linear account (no credit card required). As part of the signup flow, you will be prompted to create a workspace. Give your workspace a unique name and select the region that makes sense for you. Click through the quick start prompts until you get to your workspace home page.

You will need a Linear API key in order to call Linear as an MCP-based tool from Confluent Cloud. To create a key, click the workspace dropdown at the top left, then `Settings`. Select `Security & access` in the left-hand navigation, followed by `New API key` under `Personal API keys`. Give the key a name. Under `Permissions`, select `Only select permissions...` and then only check the boxes for `Read` and `Write`. Click `Create`. Save this API key.

## Create Bedrock and Linear connections

In order to create a remote model or MCP tool in Confluent Cloud, you will need to provide a [connection](https://docs.confluent.io/cloud/current/flink/reference/statements/create-connection.html) to an external service as a parameter, so in this step we will create the prerequisite connections.

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

The connection is successfully created when you see output:

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

In the form on the left, select the `Streamable HTTP` Transport Type, enter `https://mcp.linear.app/mcp` as the URL. Expand the `Authentication` dropdown and, in the `Custom Headers` section, enter your Linear API key as the `Bearer` header.

![MCP inspector](https://raw.githubusercontent.com/confluentinc/tutorials/master/agentic-ai-model-tool-setup/flinksql/img/mcp_inspector.png)

Scroll down, click `Connect`, and then `Approve` when you see the message `MCP Inspector is requesting access`. You'll also be prompted to select your Linear workspace.

Once you're connected, click `List Tools`. These are the tools at our disposal to build an agentic AI workflow. We're going to focus on issue creation, so note that there is a `save_issue` tool. Click that to see the fields required to create an issue.

![MCP inspector create_issue](https://raw.githubusercontent.com/confluentinc/tutorials/master/agentic-ai-model-tool-setup/flinksql/img/mcp_inspector_create_issue.png)

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

Next, create a similar LLM model, but this time also provide the MCP server connection. This is the model that we will use to invoke Linear's `save_issue` tool in the next step. The `bedrock.system_prompt` option nudges the model to prefer calling a tool over responding directly:

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
LATERAL TABLE(AI_COMPLETE('chat_listener', prompt)) as r(response);
```

You should see output like the following. It may take some time (10-15 seconds) to show up in the query results screen. Your output may be different because the underlying model is nondeterministic.

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
      ) as response;
```

You should see a JSON response indicating the status as well as the action taken. In the [Linear web app](https://linear.app/), click `All issues` and you will see a new ticket in the backlog summarizing the issue:

![Linear new issue](https://raw.githubusercontent.com/confluentinc/tutorials/master/agentic-ai-model-tool-setup/flinksql/img/linear_new_issue.png)

## Wrap up

Now that we have created a model and tools and verified that they work as expected, proceed to [Part 2](https://developer.confluent.io/confluent-tutorials/agentic-ai-streaming-agent/flinksql/) of this tutorial series.

If you aren't continuing, delete the `agentic-ai-env` environment to clean up the Confluent Cloud infrastructure created for this tutorial. Run the following command in your terminal to get the environment ID of the form `env-123456` corresponding to the environment named `agentic-ai-env`:

```shell
confluent environment list
```

Delete the environment:

```shell
confluent environment delete <ENVIRONMENT_ID>
```
