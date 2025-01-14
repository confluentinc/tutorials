import java.io.FileInputStream
import java.util.Properties

buildscript {
    repositories {
        mavenCentral()
        maven("https://packages.confluent.io/maven/")
        maven("https://jitpack.io")
        gradlePluginPortal()
    }
}

plugins {
    kotlin("jvm") version "2.0.21"
    id("com.google.protobuf") version "0.9.4"
    id("com.github.imflog.kafka-schema-registry-gradle-plugin") version "2.1.0"
    id("com.bakdata.avro") version "1.2.1"
}

group = "io.confluent.devrel"

repositories {
    mavenCentral()
    maven("https://packages.confluent.io/maven/")
    maven("https://jitpack.io")
}

schemaRegistry {
    val srProperties = Properties()
    srProperties.load(FileInputStream(File(project.projectDir.absolutePath + "/../shared/src/main/resources/confluent.properties")))

    url = srProperties.getProperty("schema.registry.url")

    val srCredTokens = srProperties.get("basic.auth.user.info").toString().split(":")
    println("***** $srCredTokens *****")
    credentials {
        username = srCredTokens[0]
        password = srCredTokens[1]
    }
    outputDirectory = "${System.getProperty("user.home")}/tmp/schema-registry-plugin"
    pretty = true

    val baseBuildDir = "${project.projectDir}/src/main"
    val avroSchemaDir = "$baseBuildDir/avro"
    val rulesetDir = "$baseBuildDir/rulesets"
    val metadataDir = "$baseBuildDir/metadata"

    register {
        subject(inputSubject =  "membership-avro-value", type = "AVRO", file = "$avroSchemaDir/membership_v1.avsc")
            .setMetadata("$metadataDir/membership_major_version_1.json")
        subject(inputSubject =  "membership-avro-value", type = "AVRO", file = "$avroSchemaDir/membership_v2.avsc")
            .setMetadata("$metadataDir/membership_major_version_2.json")
            .setRuleSet("$rulesetDir/membership_migration_rules.json")
    }
}
