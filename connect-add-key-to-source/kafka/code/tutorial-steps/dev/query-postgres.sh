echo 'SELECT * FROM cities;' | docker exec -i postgres bash -c 'psql -U $POSTGRES_USER $POSTGRES_DB'
