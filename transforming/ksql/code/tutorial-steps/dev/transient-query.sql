SELECT id, split(title, '::')[1] as title, split(title, '::')[2] AS year, genre FROM raw_movies EMIT CHANGES LIMIT 4;
