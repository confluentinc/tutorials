<!-- title: How to implement vector search-based RAG with Flink SQL in Confluent Cloud -->
<!-- description: In this tutorial, learn how to implement vector search-based RAG with Flink SQL in Confluent Cloud, with step-by-step instructions and supporting code. -->

# GenAI Part 2 of 2: How to implement vector search-based RAG with Flink SQL in Confluent Cloud

In this tutorial, you will implement a [Retrieval-Augmented Generation (RAG)](https://www.confluent.io/learn/retrieval-augmented-generation-rag/) use case. You’ll use vector search over embeddings generated in [Part 1 of this tutorial series](https://developer.confluent.io/confluent-tutorials/gen-ai-vector-embedding/flinksql/) to augment a user's query in order to understand uncommon terminology.

## Prerequisites

* Completion of Part 1 of this tutorial series with all resources left up and running: [How to generate vector embeddings for RAG with Flink SQL in Confluent Cloud](https://developer.confluent.io/confluent-tutorials/gen-ai-vector-embedding/flinksql/)
* A [MongoDB Atlas](https://www.mongodb.com/lp/cloud/atlas/try4-reg) account
* Navigate to the `gen-ai-vector-search/flinksql` directory of your `confluentinc/tutorials` GitHub repository clone

## Provision MongoDB Atlas cluster

1. In the [Atlas UI](https://cloud.mongodb.com/), select `Clusters` in the left hand navigation, and then click `Build a Cluster`.
1. Select the `Free` M0 cluster type.
1. Choose the cloud provider and region that match your Confluent Cloud environment
1. Click `Create Deployment`.
1. Create a user `admin` and copy the password that you generate for it.
1. Click `Finish and Close`.

To create your database and collection:

1. From the cluster homepage, click the `Browse Collections` button, and then `Add My Own Data`.
1. Enter the following and then click `Create`:
   * Database name: `store`
   * Collection name: `products`

## Provision MongoDB Atlas Sink Connector

You'll now land the embeddings generated in Part 1 of this tutorial series in your Atlas cluster.

1. Copy your `kafka.api.key` and `kafka.api.secret` from Part 1 (found in `../../gen-ai-vector-embedding/flinksql/datagen-product-updates-connector.json`) into `./mongodb-atlas-sink-connector.json`.
1. Replace the `ATLAS_ADMIN_USER_PASSWORD` placeholder with your `admin` user’s password.
1. In the Atlas UI, select `Clusters` in the left hand navigation, and then click `Connect`. Click `Shell` and then copy your cluster's endpoint of the form `cluster-name.abcdefg.mongodb.net`. Replace the `ATLAS_HOST` placeholder in your connector config with this value.
1. Provision the connector:
   ```shell
   confluent connect cluster create --config-file ./mongodb-atlas-sink-connector.json
   ```
1. After provisioning, verify that records show up in the `store.products` collection via the Atlas UI.

## Configure vector index

To enable vector search in Confluent Cloud, create a vector index in MongoDB Atlas:

1. In the Atlas UI, go to `Search & Vector Search` in the left hand navigation, and then `Create Search Index`.
1. Set `Search Type` to `Vector Search`.
1. Leave the index name as `vector_index`.
1. Select the `store.products` collection and click `Next`.
1. Choose `Dot Product` as the `Similarity Method` and click `Next`.
1. Click `Create Vector Search Index`.

It may take a moment for the index to build and be queryable.

## Create Flink connection to MongoDB

Before you can create a MongoDB external table in Confluent Cloud, first create a connection to the vector index. Use the same `admin` user password that you used when provisioning the sink connector earlier. Note that the endpoint needs `mongodb+srv://` prepended. If you need your environment ID of the form `env-123456`, run `confluent environment list` on the command line. Finally, if you are running Atlas in a different cloud provider or region, change that here.

```shell
confluent flink connection create mongodb-connection \
  --cloud AWS \
  --region us-east-1 \
  --type mongodb \
  --endpoint mongodb+srv://ATLAS_HOST/ \
  --username admin \
  --password ATLAS_ADMIN_USER_PASSWORD \
  --environment ENVIRONMENT_ID
```

## Create MongoDB External Table in Flink SQL

Create a MongoDB external table in the Flink SQL shell that references the connection created in the previous step. For the sake of clarity, we are only going to return the `articleType` field. Keep in mind, though, that the vector search will run against embeddings generated from the concatenation of all product catalog fields.

```sql
CREATE TABLE mongodb_products (
  articleType STRING,
  vector ARRAY<FLOAT>
) WITH (
  'connector' = 'mongodb',
  'mongodb.connection' = 'mongodb-connection',
  'mongodb.database' = 'store',
  'mongodb.collection' = 'products',
  'mongodb.index' = 'vector_index',
  'mongodb.embedding_column' = 'vector',
  'mongodb.numCandidates' = '5'
);

```

## Populate a table with sample user queries

In the Flink SQL shell, simulate a stream of uncommon slang user queries for footwear (kicks, footies), shorts (cutoffs), and a hat (lid):

```sql
CREATE TABLE queries (            
    query STRING
) WITH (
  'value.format' = 'json-registry'
);
```

```sql
INSERT INTO queries values ('kicks'), ('footies'), ('cutoffs'), ('lid');
```

## Generate query vector embeddings

Create a table for query vectors using the same embedding model used for product embeddings in Part 1:

```sql
CREATE TABLE query_vectors (            
    query  STRING,
    vector ARRAY<FLOAT>
) WITH (
  'value.format' = 'json-registry'
);
```

```sql
INSERT INTO query_vectors
(
    SELECT query, vector
    FROM queries,
    LATERAL TABLE(ML_PREDICT('vector_embedding', query))
);
```

## Run the vector search

Query the vector index to find products for each slang query:

```sql
SELECT query, search_results
FROM query_vectors,
LATERAL TABLE(VECTOR_SEARCH_AGG(mongodb_products, DESCRIPTOR(vector), query_vectors.vector, 3));
```

Your results will vary from the following depending on the products that you generated in Part 1 of this tutorial series, but you should see sensible search results that match the slang queries:

```plaintext
query   search_results

footies [(shoes, [0.020776896, 0.023529341, ...
cutoffs [(shorts, [0.0352604, -0.0070558237, ...
kicks   [(sandals, [0.030727627, -0.009568145, ...
lid     [(hat, [0.007294555, 0.022405202, ...
```

## Clean up

When you finish experimenting, delete the `confluent-rag_environment` environment in order to clean up the Confluent Cloud infrastructure created for this tutorial. Run the following command in your terminal to get the environment ID of the form `env-123456` corresponding to the environment named `confluent-rag_environment`:

```shell
confluent environment list
```

Delete the environment:

```shell
confluent environment delete <ENVIRONMENT_ID>
```
