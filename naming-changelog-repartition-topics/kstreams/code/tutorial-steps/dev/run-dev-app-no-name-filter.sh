
sh -c 'echo $$ > configuration/app.pid; exec java -jar build/libs/naming-changelog-repartition-topics-standalone-0.0.1.jar configuration/dev.properties filter-only'
