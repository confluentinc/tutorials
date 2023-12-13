SELECT author, title FROM all_publications WHERE author = 'George R. R. Martin' EMIT CHANGES LIMIT 4;
