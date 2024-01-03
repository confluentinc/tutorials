# Running python source connectors and SMTs

## Intro

CSID Labs have developed tools and libraries that allow you to write and run python source connectors and SMTs.

In addition to letting you use your favourite language (python), they are also very simple to use. You can write a `Hello World` source connector with just 2 lines of codes.

More details:
- Source: https://github.com/confluentinc/csid-python-base
- How it works: https://docs.google.com/presentation/d/1cdfVa2A5FQXwo5Oxi9FUEscQp2z78SAo5xfvjNNZhIg/

## Running a python source connector

This tutorial uses a complete cluster running on `docker`. Just make sure you have Docker Desktop installed on your computer and you're ready to go!

### Start the cluster

1. Open a terminal and go to the tutorial folder.
2. Start the cluster: `docker-compose up -d`
3. Wait for a few seconds and run: `docker-compose ps`. Everything container instance should be marked as running.  

### Start a connector and an SMT

From there, you have to create a connector in the Connect cluster.  

There are 3 examples provided with this tutorial in the `json`folder. They all use a python connector and the last one also includes a python SMT.

1. `connector-fields.json`: this example publishes JSON messages containing 6 fields. This corresponds to the `code/speed_traps.py` script.
2. `connector-full-text.json`: this example publishes messages with one big string as the value. This is in the `code/full_text.py` script.
3. `connector-with-smt.json`: this example publishes simple messages and the same script also contains an SMT that is modifying the messages. It can be found in the `code/connector_with_smt.py` script.

To create a connector you can use `curl` or you can use the Control Center UI. Since we already have a terminal open, let's use that.

Run `curl localhost:8083/connectors -X POST --json @/json/connector-fields.json` to create a connector with the example 1 above.

To check that everything is running you can either:
- Open Control Center at http://localhost:9021 and go to the Topics panel. Select the topic starting with `test-topic` and click on the Messages tab. You should see messages coming in.
  ![](img/control-center.png)

- Open the Docker Desktop dashboard, expand the cluster, click on the `connect` container.
  ![](img/docker-desktop.png)
  The Logs tab should list messages being published by the connector and modified by the SMT if there's one. 
  ![](img/docker-desktop-log.png)

### Note on SMTs

SMTs are functions that transform the messages before they are saved into the source topic by the source connector. 

They receive the messages with some context and return the transformed (or not) messages. They can even return `None` to filter messages out (in other words, those won't be saved into the input topic). 

In our example (no 3 above), the connector and the SMT are on the same script. This is for convenience but not mandatory at all. As a matter of fact, in the vast majority of the case, the SMTs will be fully separated from the connectors.

## A brief look at the code

