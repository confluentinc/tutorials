const fs = require("fs");

const TOPIC_NAME = "readings";

const schema = {
  type: "record",
  namespace: "io.confluent.developer.avro",
  name: "TempReading",
  fields: [
    { name: "deviceId", type: "string" },
    { name: "temperature", type: "float" },
  ],
};

function parsePropertiesFile(filePath) {
  const content = fs.readFileSync(filePath, "utf-8");
  const properties = {};

  content.split("\n").forEach((line) => {
    line = line.trim();
    if (line && !line.startsWith("#")) {
      const [key, ...valueParts] = line.split("=");
      if (key) {
        properties[key.trim()] = valueParts.join("=").trim();
      }
    }
  });

  return properties;
}

function parseCommandLineArgs() {
  const args = {};

  for (let i = 2; i < process.argv.length; i++) {
    if (process.argv[i].startsWith("--")) {
      const key = process.argv[i].substring(2);
      const value = process.argv[i + 1];
      if (value && !value.startsWith("--")) {
        args[key] = value;
        i++;
      }
    }
  }

  return args;
}

function loadConfigurationFromArgs(isConsumer) {
  const args = parseCommandLineArgs();

  if (!args["kafka-properties-file"] || !args["sr-properties-file"]) {
    console.error(
      "--kafka-properties-file and --sr-properties-file are required",
    );
    process.exit(1);
  }

  const kafkaProps = parsePropertiesFile(args["kafka-properties-file"]);
  if (isConsumer) {
    kafkaProps["group.id"] = "kafka-sr-example-group";
    kafkaProps["auto.offset.reset"] = "earliest";
  }

  const srFileProps = parsePropertiesFile(args["sr-properties-file"]);
  const srProps = {
    baseURLs: [srFileProps.url],
  };

  if (srFileProps.hasOwnProperty("basic.auth.user.info")) {
    srProps["basicAuthCredentials"] = {
      credentialsSource: "USER_INFO",
      userInfo: srFileProps["basic.auth.user.info"],
    };
  }

  return { kafkaProps, srProps };
}

module.exports = {
  TOPIC_NAME,
  schema,
  loadConfigurationFromArgs,
};
