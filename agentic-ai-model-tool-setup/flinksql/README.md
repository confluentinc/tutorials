<!-- title: How to set up models and MCP tools to be used in agentic AI workflows with Flink SQL in Confluent Cloud -->
<!-- description: In this tutorial, learn how to set up models and MCP tools to be used in agentic AI workflows with Flink SQL in Confluent Cloud, with step-by-step instructions and supporting code. -->

# Agentic AI Part 1 of 2: LLM model and MCP tool setup in Confluent Cloud

In Part 1 of this tutorial series, you will set up and test the infrastructure and third-party dependencies required for an agentic AI use case: a chat listener that creates concise tasks in a project management platform. This is a prime example of MCP tool integration: LLMs are strong at summarizing a customer's natural language, but they lack awareness of your organization's project management platform and the context and connectivity needed to integrate with such an external system.

After you finish this tutorial, in [Part 2](https://developer.confluent.io/confluent-tutorials/agentic-ai-streaming-agent/flinksql/) of the series you will continue to build and evolve a streaming agent for this use case.

## Prerequisites

- A [Confluent Cloud](https://confluent.cloud/signup) account
- The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html) installed on your machine
- An [OpenAI](https://auth.openai.com/create-account) account
- A [Linear](https://linear.app/) account. Sign up for the free plan.
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

Run the plugin to create the Confluent Cloud resources needed for this tutorial. Note that you may specify a different cloud provider (`gcp` or `azure`) or region. You can find supported regions in a given cloud provider by running `confluent kafka region list --cloud <CLOUD>`. The plugin should complete in under a minute.

```shell
confluent quickstart \
  --environment-name agentic-ai-env \
  --kafka-cluster-name agentic-ai-cluster \
  --compute-pool-name agentic-ai-pool
```

## Create Linear and OpenAI credentials

[Log in](https://auth.openai.com/log-in) to the OpenAI platform. Navigate to the [Project API keys page](https://platform.openai.com/api-keys) and click `Create new secret key`. Save this key because we will need it later when creating a remote model in Flink.

Next, [log in to Linear](https://linear.app/login).

When prompted, give your workspace a unique name and click through the quick start prompts until you get to your workspace home page.

To create a Linear API key, click the workspace dropdown at the top left, then `Settings`. Select `Security & access` in the left-hand navigation, followed by `New API key` under `Personal API keys`. Give the key a name. Under `Permissions`, select `Only select permissions...` and then only check the boxes for `Read` and `Write`. Click `Create`. Save this API key.

## Create Linear and OpenAI connections

Start a Flink SQL shell:

```shell
confluent flink shell --compute-pool \
  $(confluent flink compute-pool list -o json | jq -r ".[0].id")
```

Linear offers both streamable HTTP and SSE-based [MCP endpoints](https://linear.app/docs/mcp#general). Paste your Linear API key into the following statement to create a connection to Linear's streamable HTTP MCP server:

```sql
CREATE CONNECTION `linear-mcp-connection`
  WITH (
    'type' = 'MCP_SERVER',
    'endpoint' = 'https://mcp.linear.app/mcp',
    'transport-type' = 'streamable_http',
    'token' = '<LINEAR_API_KEY>'
  );
```

Next, paste your OpenAI API key into the following statement to create a connection to OpenAI's [Chat Completions API](https://platform.openai.com/docs/api-reference/chat/create):

```sql
CREATE CONNECTION `openai-connection`
  WITH (
    'type' = 'openai',
    'endpoint' = 'https://api.openai.com/v1/chat/completions',
    'api-key' = '<OPENAI_API_KEY>'
  );
```

## Inspect the Linear MCP server

While you can sometimes point a model at an MCP tool directly, it helps to inspect the tool to see which operations are available and what parameters they require.

Run the following command to start an MCP server inspection tool:

```shell
npx @modelcontextprotocol/inspector@latest https://mcp.linear.app/mcp
```

In the form on the left, select the `Streamable HTTP` Transport Type, enter `https://mcp.linear.app/mcp` as the URL, and enter your Linear API key as the `Bearer` header.

![MCP inspector](https://raw.githubusercontent.com/confluentinc/tutorials/master/agentic-ai-model-tool-setup/flinksql/img/mcp_inspector.png)

Scroll down, click `Connect`, and then approve the login. Once you're connected, click `List Tools`. These are the tools at our disposal to build an agentic AI workflow. We're going to focus on issue creation, so note that there is a `create_issue` tool. Click that to see the fields required to create an issue.

![MCP inspector create_issue](https://raw.githubusercontent.com/confluentinc/tutorials/master/agentic-ai-model-tool-setup/flinksql/img/mcp_inspector_create_issue.png)

You'll notice a required `team` field that you must provide when calling the tool. You can get your team ID (a GUID) by running the following command. Be sure to substitute your Linear API key:

```shell
curl \
  -X POST \
  -H "Content-Type: application/json" \
  -H "Authorization: <LINEAR_API_KEY>" \
  --data '{
    "query": "query Teams { teams { nodes { id name } }}"
  }' \
  https://api.linear.app/graphql
```

## Create models

In the Flink SQL shell, create a `gpt-4o` model using the OpenAI connection created earlier:

```sql
CREATE MODEL chat_listener
INPUT(prompt STRING)
OUTPUT(response STRING)
WITH (
  'provider' = 'openai',
  'task' = 'text_generation',
  'openai.model_version' = 'gpt-4o',
  'openai.connection' = 'openai-connection'
);
```

Next, create a similar LLM model, but this time also provide the MCP server connection.

```sql
CREATE MODEL linear_mcp_model
INPUT(prompt STRING)
OUTPUT(response STRING)
WITH (
  'provider' = 'openai',
  'task' = 'text_generation',
  'openai.model_version' = 'gpt-4o',
  'openai.connection' = 'openai-connection',
  'mcp.connection' = 'linear-mcp-connection'
);
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

You should see output like the following. Your output may be different because the underlying model is nondeterministic.

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
          MAP['create_issue', 'Create a new issue'],
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
