while [ $(curl -s -o /dev/null -w %{http_code} http://localhost:8088/) -eq 000 ] ; do sleep 5 ; done;
# Back off for KSQL server to get out of the initialization phase.
sleep 5
