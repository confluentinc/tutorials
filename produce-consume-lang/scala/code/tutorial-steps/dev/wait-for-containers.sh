# Wait for Schema Registry to become available
while [ $(curl -s -o /dev/null -w %{http_code} http://localhost:8081/) -eq 000 ]
do
  echo -e $(date) "Schema Registry HTTP state: " $(curl -s -o /dev/null -w %{http_code} http://localhost:8081/) " (waiting for 200)"
  sleep 5
done
