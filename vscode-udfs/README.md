<!-- title: How to use Confluent for VS Code to create a sample Java UDF in Confluent Cloud for Apache Flink -->
<!-- description: In this tutorial, learn how to develop, upload, register and execute Flink UDFs with Confluent for VS Code, with step-by-step instructions. -->

# How to use Confluent for VS Code to create a sample Java UDF in Confluent Cloud for Apache Flink

In this tutorial, you'll follow along step-by-step to build a Java UDF, then deploy it in Confluent Cloud for Apache Flink. 

## Prerequisites

1. You will need a Confluent Cloud [account](https://www.confluent.io/confluent-cloud/tryfree/) if you haven't got one already. 

2. [Install Confluent for VS Code](https://docs.confluent.io/cloud/current/client-apps/vs-code-extension.html).

3. Get [Java version 11 or 17](https://openjdk.org/install/).

4. You will also need to [install Maven](https://maven.apache.org/).

## Build and Deploy UDFs

In the VS Code Activity Bar, click the Confluent icon.

![Activity Bar Selection](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-produce-and-consume/img/activity-bar-selection.png)

If you have many extensions installed, you may need to click … to access 'Additional Views' and select 'Confluent' from the context menu.

![Activity Bar Selection via Additional Views](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-produce-and-consume/img/via-views.png)

In the Side Bar, click 'Connect to Confluent Cloud', and in the permission dialog, click 'Allow'.

![Connect to Confluent Cloud](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-produce-and-consume/img/connect-to-cc.png)

![Allow opening in new window](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-produce-and-consume/img/new-window.png)

The web browser opens to the Confluent Cloud login page.

Enter your credentials and click 'Log in'.

![Confluent Cloud Log In Page](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-produce-and-consume/img/login.png)

The web browser shows the 'Authentication Complete' page.

![Head to Confluent Cloud page](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-produce-and-consume/img/auth-complete-page.png)

Open the Confluent Cloud Console and [follow these steps](https://docs.confluent.io/cloud/current/get-started/index.html#section-1-create-a-cluster-and-add-a-topic) to create an environment.

Next, create a Flink compute pool by following [these steps](https://docs.confluent.io/cloud/current/flink/operate-and-deploy/create-compute-pool.html).

While that pool is provisioning, return to your editor and confirm that your Confluent Cloud resources are displayed in the Side Bar.

Next, click on "Support" in the sidebar and then "Generate Project From Template". Search "UDFs" and select the Flink UDF template and select your desired directory. 

Navigate to the directory you generate the template in. Time to build the examples!

First, generate the maven wrapper jar:

```shell
mvn wrapper:wrapper 
```

You may need to run:

```shell
chmod +x mvnw
```

then: 

```shell
./mvnw clean package
```

In the sidebar, click "Support" then "Generate Project From Template". Search "UDFs" and click on the UDF template generator. 

Navigate to the folder you generated the template in. 

This should produce a jar for each of the modules, which can be uploaded to Confluent Cloud.

For example:

```shell
> find . -wholename '*/target/*jar' | grep -v original
./udfs-simple/target/udfs-simple-1.0.0.jar
```

You should then upload the jars to Confluent Cloud, and create a UDF in your catalog.
More details on how to do that can be found
[here](https://docs.confluent.io/cloud/current/flink/how-to-guides/create-udf.html).

Here's an example of the command you'd run to register the function in your Flink workspace once you've uploaded the artifact following the instructions above:

```sql
CREATE FUNCTION sum_integers
AS 'io.confluent.udf.examples.log.LogSumScalarFunction'
USING JAR 'confluent-artifact://cfa-v7r61n';
```
Remember to replace `cfa-v7r61n` with your own artifact id.

Then you can test the function by running this statement:

```sql
SELECT sum_integers(CAST(5 AS INT), CAST(3 AS INT));
```

## Inspect the Code

In the `pom.xml` you'll see the following:

```xml
        <dependency>
            <groupId>org.apache.flink</groupId>
            <artifactId>flink-table-api-java</artifactId>
            <scope>provided</scope>
        </dependency>
```

This is vital to the execution of user-defined functions with Flink. This is where you'll get the base classes 
you need to implement a UDF. If you look at `LogSumScalarFunction`, you'll see an import of the `ScalarFunction` class:

```java
import org.apache.flink.table.functions.ScalarFunction;
```

Then this class is extended to sum two numbers and log messages:

```java
/* This class is a SumScalar function that logs messages at different levels */
public class LogSumScalarFunction extends ScalarFunction {

    private static final Logger LOGGER = LogManager.getLogger();

    public int eval(int a, int b) {
        String value = String.format("SumScalar of %d and %d", a, b);
        Date now = new Date();

        // You can choose the logging level for log messages.
        LOGGER.info(value + " info log messages by log4j logger --- " + now);
        LOGGER.error(value + " error log messages by log4j logger --- " + now);
        LOGGER.warn(value + " warn log messages by log4j logger --- " + now);
        LOGGER.debug(value + " debug log messages by log4j logger --- " + now);
        return a + b;
    }
}
```
