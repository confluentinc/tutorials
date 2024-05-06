CREATE STREAM rock_songs (artist VARCHAR, title VARCHAR)
    WITH (kafka_topic='rock_songs', partitions=1, value_format='avro');

INSERT INTO rock_songs (artist, title) VALUES ('Metallica', 'Fade to Black');
INSERT INTO rock_songs (artist, title) VALUES ('Smashing Pumpkins', 'Today');
INSERT INTO rock_songs (artist, title) VALUES ('Pink Floyd', 'Another Brick in the Wall');
INSERT INTO rock_songs (artist, title) VALUES ('Van Halen', 'Jump');
INSERT INTO rock_songs (artist, title) VALUES ('Led Zeppelin', 'Kashmir');

CREATE STREAM classical_songs (artist VARCHAR, title VARCHAR)
    WITH (kafka_topic='classical_songs', partitions=1, value_format='avro');

INSERT INTO classical_songs (artist, title) VALUES ('Wolfgang Amadeus Mozart', 'The Magic Flute');
INSERT INTO classical_songs (artist, title) VALUES ('Johann Pachelbel', 'Canon');
INSERT INTO classical_songs (artist, title) VALUES ('Ludwig van Beethoven', 'Symphony No. 5');
INSERT INTO classical_songs (artist, title) VALUES ('Edward Elgar', 'Pomp and Circumstance');

CREATE STREAM all_songs (artist VARCHAR, title VARCHAR, genre VARCHAR)
    WITH (kafka_topic='all_songs', partitions=1, value_format='avro');

INSERT INTO all_songs SELECT artist, title, 'rock' AS genre FROM rock_songs;

INSERT INTO all_songs SELECT artist, title, 'classical' AS genre FROM classical_songs;
