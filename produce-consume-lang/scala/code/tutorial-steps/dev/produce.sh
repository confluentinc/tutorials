./gradlew -q --console=plain shadowJar
java -jar app-producer/build/libs/app-producer-0.1.0-SNAPSHOT.jar "$(cat data.csv)"