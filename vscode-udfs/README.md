<!-- title: How to create UDFs with Confluent for VS Code -->
<!-- description: In this tutorial, learn how to develop, upload, register and execute Flink UDFs with Confluent for VS Code, with step-by-step instructions. -->

# VSCode Flink UDFs

[Install Confluent for VS Code](https://docs.confluent.io/cloud/current/client-apps/vs-code-extension.html).

In the VS Code Activity Bar, click the Confluent icon.

![Activity Bar Selection](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-produce-and-consume/img/activity-bar-selection.png)

If you have many extensions installed, you may need to click … to access 'Additional Views' and select 'Confluent' from the context menu.

![Activity Bar Selection via Additional Views](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-produce-and-consume/img/via-views.png)

In the Side Bar, click 'Connect to Confluent Cloud', and in the permission dialog, click 'Allow'.

![Connect to Confluent Cloud](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-produce-and-consume/img/connect-to-cc.png)

![Allow opening in new window](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-ccloud-quickstart/img/new-window.png)

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

## Building the examples

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
./udfs-with-dependencies/target/udfs-with-dependencies-1.0.0.jar
```

You should then upload the jars to Confluent Cloud, and create a UDF in your catalog.
More details on how to do that can be found
[here](https://docs.confluent.io/cloud/current/flink/how-to-guides/create-udf.html).

Here's an example of the command you'd run to register the function in your Flink workspace once you've uploaded the artifact following the instructions above:

```sql
CREATE FUNCTION my_udf
AS 'io.confluent.udf.examples.log.LogSumScalarFunction'
USING JAR 'confluent-artifact://cfa-v7r61n';
```
Remember to replace `cfa-v7r61n` with your own artifact id.

Then you can test the function by running this statement:

```sql
SELECT my_udf(CAST(5 AS INT), CAST(3 AS INT));
```
