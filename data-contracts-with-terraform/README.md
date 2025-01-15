<!-- title: How to manage data contracts with terraform -->
<!-- description: In this tutorial we explore using terraform to manage data contracts for event streams. -->

# Manage Data Contracts with Terraform

Data contracts consists not only of the schemas to define the data, but also rulesets allowing for more fine-grained validations,
controls, and discovery.In this tutorial, we'll evolve a couple of schemas and add data quality and migration rules.We'll also
explore tagging those schemas, fields, and rules for data discovery.

## Running the Example

In this tutorial we'll create Confluent Cloud infrastructure - including a Kafka cluster and Schema Registry. Then we'll create
a Kafka topic named `membership-avro` to store `Membership` events. The Apache Avro schema is maintained an managed in this repo
along with metadata and migration rules about those schemas. 

We will evolve the `membership` schema, refactoring the events to encapsulate the date-related fields of version 1 into its
own `record` type in version 2. Typically this would be a breaking change. However, data migration rules in the schema registry
allow us to perform this schema change without breaking producers or consumers. At the time this is written, this functionality
is only available to JVM-based Confluent client implementations. We'll update this example as our non-JVM clients evolve.

### Prerequisites

Here are the tools needed to run this tutorial:
* [Confluent Cloud](http://confluent.cloud)
* [Confluent CLI](https://docs.confluent.io/confluent-cli/current/install.html)
* [Terraform](https://developer.hashicorp.com/terraform/install?product_intent=terraform)
* [jq](https://jqlang.github.io/jq/)
* JDK 17
* IDE of choice

### Executing Terraform

To create Confluent Cloud assets, change to the `cc` subdirectory. We'll step through the commands and what they do.

#### Create Confluent Cloud Assets

Export the Confluent Cloud organization ID to a terraform environment variable:

```shell
export TF_VAR_org_id=$(confluent organization list -o json | jq -c -r '.[] | select(.is_current)' | jq '.id')
```

Now, we are ready to initialize the terraform environment, create a `plan` and `apply` said plan to create CC assets:

```shell
terraform init
terraform plan -out "tfplan"
terraform apply "tfplan"
```

#### Prepare Client Properties

This demo has tailored the output of `terraform apply` to return the properties needed to connect to Confluent Cloud. The command below will 
reformat the names of those properties into the names used in Kafka Client configurations, then export those outputs to a properties file 
in our project:

```shell
terraform output -json | \
  jq -r 'to_entries | map( {key: .key|tostring|split("_")|join("."), value: .value} ) | map("\(.key)=\(.value.value)")' | while read -r line ; do echo "$line"; \
  done > ../shared/src/main/resources/confluent.properties
```

All Kafka Client code in this project loads connection properties form `shared/src/main/resources/confluent.properties`. For an example of this 
properties file, see [confluent.properties.orig](shared/src/main/resources/confluent.properties.orig).

### Schema Evolution


## Teardown

When you're done with the tutorial, issue this command from the `cc` directory to destroy the Confluent Cloud environment
we created:

```shell
terraform destroy -auto-approve
```

Check the Confluent Cloud console to ensure this environment no longer exists.


