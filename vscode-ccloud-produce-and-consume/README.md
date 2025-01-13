# VSCode Produce And Consume (CC Version)

[Install Confluent for VS Code](https://docs.confluent.io/cloud/current/client-apps/vs-code-extension.html#cc-vscode-extension) for Confluent Cloud.

In the VS Code Activity Bar, click the Confluent icon.

![Activity Bar Selection](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-ccloud-quickstart/img/walkthru1.png)

If you have many extensions installed, you may need to click … to access Additional Views and select Confluent from the context menu.

![Activity Bar Selection via Additional Views](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-ccloud-quickstart/img/walkthru2.png)

In the Side Bar, click Connect to Confluent Cloud, and in the permission dialog, click Allow.

![Connect to Confluent Cloud](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-ccloud-quickstart/img/walkthru3.png)

![Allow opening in new window](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-ccloud-quickstart/img/walkthru4.png)

The web browser opens to the Confluent Cloud login page.

Enter your credentials and click Log in.

![Confluent Cloud Log In Page](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-ccloud-quickstart/img/walkthru5.png)

The web browser shows the Authentication Complete page. You can click "Open Visual Studio Code" if prompted, but for the purposes of this walkthrough, we will click "head to Confluent Cloud".

![Head to Confluent Cloud page](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-ccloud-quickstart/img/walkthru6.png)

Open the Confluent Cloud Console and [follow these steps](https://docs.confluent.io/cloud/current/get-started/index.html#section-1-create-a-cluster-and-add-a-topic) to create an environment.

Next, create a Basic Kafka cluster by following [these steps](https://docs.confluent.io/cloud/current/get-started/index.html#step-1-create-a-ak-cluster-in-ccloud).

Select Topics from the navigation menu in Confluent Cloud, then select Create topic with name `test-topic`.

Return to VS Code and confirm that your Confluent Cloud resources are displayed in the Side Bar.

Create the following file named message.json in your folder:

```json
{
  "headers": [
    {
      "key": "sample.header",
      "value": "sample_header_value"
    }
  ],
  "key": 1237,
  "value": {
    "type": "JSON",
    "ordertime": 1499986565014,
    "orderid": 1237,
    "itemid": "Customer Produce",
    "orderunits": 2.621748519463491,
    "address": {
      "city": "City_",
      "state": "State_",
      "zipcode": 79416
    }
  }
}
```

Click the produce button and select `message.json` from your files.

![Producing A Message](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-ccloud-quickstart/img/walkthru7.png)

Navigate to the topics resource tab, click ‘play’, and see your message coming into the VSCode message viewer!

![Viewing A Message](https://raw.githubusercontent.com/confluentinc/tutorials/master/vscode-ccloud-quickstart/img/walkthru7.png)
