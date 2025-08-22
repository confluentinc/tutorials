<!-- title: How to generate vector embeddings for RAG with Flink SQL in Confluent Cloud -->
<!-- description: In this tutorial, learn how to generate vector embeddings for RAG with Flink SQL in Confluent Cloud, with step-by-step instructions and supporting code. -->

# GenAI Part 1 of 2: How to generate vector embeddings for RAG with Flink SQL in Confluent Cloud

In Part 1 of this tutorial series, you will generate vector embeddings on retail product catalog data. A source connector ingests and writes unstructured source data to a topic. Flink SQL then converts this data into vector embeddings and inserts into a new topic.

This tutorial is a building block for real-time GenAI applications including [RAG](https://www.confluent.io/learn/retrieval-augmented-generation-rag/) and is based on the webinar [How to Build RAG Using Confluent with Flink AI Model Inference and MongoDB](https://www.confluent.io/resources/online-talk/rag-tutorial-with-flink-ai-model-inference-mongodb/).

Once vector encoding is complete, [Part 2](https://developer.confluent.io/confluent-tutorials/gen-ai-vector-search/flinksql/) of this tutorial series leverages vector search over the embeddings to build a RAG-enabled query engine that is robust with respect to uncommon or slang terminology. Other use cases that build on vector embeddings and vector search include:

* RAG-enabled GenAI chatbots, content discovery, and recommendation engines. E.g., retrieving user profile data and questions to match the size of clothing and the fashion type requested by the user's query. The results are sent to an LLM as context to augment the prompt and mitigate hallucinations, ensuring that the LLM generates specific and accurate product recommendations.
* ML-driven search for real-time fraud detection, anomaly detection, or forecasting

## Prerequisites

* A [Confluent Cloud](https://confluent.cloud/signup) account
* The [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html)
* [jq](https://jqlang.org/) for parsing command line JSON output
* An [OpenAI developer platform](https://platform.openai.com/docs/overview) account and API key. Once you [sign up](https://platform.openai.com/signup) and add money to your account, go to the [Project API keys](https://platform.openai.com/api-keys) page and click `Create new secret key`. Copy this key, as we will need it later when creating a remote model in Flink.
* Clone the `confluentinc/tutorials` GitHub repository (if you haven't already) and navigate to the `tutorials/gen-ai-vector-embedding/flinksql` directory:

```shell
git clone git@github.com:confluentinc/tutorials.git
cd tutorials/gen-ai-vector-embedding/flinksql
```

## Provision Kafka and Flink infrastructure in Confluent Cloud

The `confluent-flink-quickstart` CLI plugin creates all the resources that you need to get started with Confluent Cloud for Apache Flink. Install it by running:

```shell
confluent plugin install confluent-flink-quickstart
```

Run the plugin as follows to create the Confluent Cloud resources needed for this tutorial and generate a Table API client configuration file. Note that you may specify a different cloud provider (`gcp` or `azure`) or region. You can find supported regions for a given cloud provider by running `confluent flink region list --cloud <CLOUD>`.

```shell
confluent flink quickstart \
    --name confluent-rag \
    --max-cfu 10 \
    --region us-east-1 \
    --cloud aws
```

Once the infrastructure is provisioned, you will drop into a Flink SQL shell. Leave this terminal window open as we will return to it in later steps to execute SQL statements.

## Generate synthetic product update records

Before converting data into vectors, let's generate some sample retail data by adding a Datagen Source Connector.

First, create an API key for the connector to connect to Kafka:

```shell
confluent api-key create --resource $(confluent kafka cluster describe -o json | jq -r .id)
```

Associate that API key with the Kafka cluster in order to make it more convenient to run later commands:

```shell
confluent api-key use <API KEY>
```

Substitute the API key and secret for `YOUR_API_KEY` and `YOUR_API_SECRET`, respectively, in `datagen-product-updates-connector.json`.

Provision the connector:

```shell
confluent connect cluster create --config-file ./datagen-product-updates-connector.json
```

Once the connector is provisioned, verify that the `product-updates` topic is populated. You may need to wait a minute if you get an error `Error: topic "product-updates" does not exist`.

```shell
confluent kafka topic consume product-updates --from-beginning --value-format jsonschema
```

You should see messages like:

```plaintext
{"ageGroup":"adult","articleType":"shirt","baseColor":"blue","brandName":"Lalonde","count":9,"fashionType":"core","gender":"female","price":67.22,"product_id":443150,"season":"fall","size":"petite","store_id":21}
```

Enter `Ctrl+C` to exit the console consumer.

## Create OpenAI Flink connection

Paste your OpenAI API key in the following command to create a connection named `openai-vector-connection` that we will use in Flink SQL to generate vector embeddings. This connection resides in AWS region `us-east-1`. Substitute your infrastructure location if you provisioned in a different cloud provider or region.

```shell
confluent flink connection create openai-embedding-connection \
    --cloud aws \
    --region us-east-1 \
    --environment $(confluent environment describe -o json | jq -r .id) \
    --type openai \
    --endpoint 'https://api.openai.com/v1/embeddings' \
    --api-key '<OPENAI API KEY>'
```

## Create model for vector embeddings

Return to the Flink SQL shell that the `confluent flink quickstart ...` command opened. If you closed out of it you can open a new shell session by running `confluent flink shell`.

Before we can generate vector embeddings, we need to define a [remote model](https://docs.confluent.io/cloud/current/flink/reference/functions/model-inference-functions.html) in Confluent Cloud for Apache Flink:

```shell
CREATE MODEL `vector_embedding`
INPUT (input STRING)
OUTPUT (vector ARRAY<FLOAT>)
WITH(
  'TASK' = 'embedding',
  'PROVIDER' = 'openai',
  'OPENAI.CONNECTION' = 'openai-embedding-connection'
);
```

Test that you can generate vector embeddings on the `articleType` field of the `product-updates` table:

```sql
SELECT articleType, vector
FROM `product-updates`,
LATERAL TABLE(ML_PREDICT('vector_embedding', articleType))
LIMIT 1;
```

You should see output like:

```plaintext
articleType vector
sweater     [-0.005236239, -0.016405802, ...
```

## Generate embeddings on concatenated product content

In the SQL shell, create a derived product catalog table that includes a new column to hold the concatenated text for each product and a column for the vector embedding:

```sql
CREATE TABLE product_vector (
    store_id    INT,                 
    product_id  INT,                         
    `count`     INT,                         
    articleType STRING,                      
    size        STRING,                      
    fashionType STRING,                      
    brandName   STRING,                      
    baseColor   STRING,                      
    gender      STRING,                      
    ageGroup    STRING,                     
    price       DOUBLE,                     
    season      STRING,
    content     STRING,
    vector      ARRAY<FLOAT>
) WITH (
  'value.format' = 'json-registry'
);
```

Now populate this table via the following `INSERT SELECT` statement:

```sql
INSERT INTO product_vector
(
    WITH product_content as (
        SELECT
            *,
            concat_ws(' ', size, ageGroup, gender, season, fashionType, brandName, baseColor, articleType,
                      ', price: ' || cast(price as string), ', store number: ' || cast(store_id as string),
                      ', product id: ' || cast(product_id as string)
            ) as content
        FROM `product-updates`
    )
    SELECT store_id, product_id, `count`, articleType, size, fashionType, brandName,
           baseColor, gender, ageGroup, price, season, content, vector
    FROM product_content,
    LATERAL TABLE(ML_PREDICT('vector_embedding', content))
);
```

This command will run continuously, so press `Enter` to detach once prompted to do so.

As the last step, query the generated embeddings in the `product_vector` table:

```sql
SELECT vector, content
FROM product_vector;
```

You should see output like:

```plaintext
vector                   content                
[0.060939293, -0.022118… petite adult female su…
[0.022782113, -0.020454… medium infant female f…
[0.057449587, 0.0012455… small infant female su…
[0.025510406, -0.007447… extralarge child male …
...
```

## Next step: Add a sink connector for your vector store and run vector search

In this tutorial, you learned how to generate vector embeddings from string data in Kafka messages using Flink SQL.

As a next step, continue to Part 2 of this tutorial series: [How to implement vector search-based RAG with Flink SQL in Confluent Cloud](https://developer.confluent.io/confluent-tutorials/gen-ai-vector-search/flinksql/)

The next tutorial in this series stores these embeddings in MongoDB Atlas. If you would like to write the embeddings to a different vector store, you can deploy a sink connector on Confluent Cloud. Navigate to the `Connectors` page in the Confluent Cloud Console or use the Confluent CLI. This setup enables you to continuously stream real-time vector embeddings from Flink SQL into your vector database.

For guidance on setting up a vector database sink connector, refer to the following resources:

* [MongoDB](https://docs.confluent.io/cloud/current/connectors/cc-mongo-db-sink/cc-mongo-db-sink.html)
* [Elasticsearch](https://docs.confluent.io/cloud/current/connectors/cc-elasticsearch-service-sink.html)
* [Pinecone](https://docs.confluent.io/cloud/current/connectors/cc-pinecone-sink.html)
* [Couchbase](https://www.confluent.io/hub/couchbase/kafka-connect-couchbase)
* [SingleStore](https://www.confluent.io/hub/singlestore/singlestore-kafka-connector)
* [Milvus by Zilliz](https://www.confluent.io/hub/zilliz/kafka-connect-milvus)
* [Neo4j](https://www.confluent.io/hub/neo4j/kafka-connect-neo4j)
* [Qdrant](https://www.confluent.io/hub/qdrant/qdrant-kafka)

## Clean up

If you are not continuing to [Part 2](https://developer.confluent.io/confluent-tutorials/gen-ai-vector-search/flinksql/) of this tutorial series, delete the `confluent-rag_environment` environment in order to clean up the Confluent Cloud infrastructure created for this tutorial. Run the following command in your terminal to get the environment ID of the form `env-123456` corresponding to the environment named `confluent-rag_environment`:

```shell
confluent environment list
```

Delete the environment:

```shell
confluent environment delete <ENVIRONMENT_ID>
```
