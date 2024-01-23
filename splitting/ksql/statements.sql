CREATE STREAM actingevents (name VARCHAR, title VARCHAR, genre VARCHAR)
    WITH (KAFKA_TOPIC = 'acting-events', PARTITIONS = 1, VALUE_FORMAT = 'AVRO');

INSERT INTO ACTINGEVENTS (name, title,genre) VALUES ('Bill Murray', 'Ghostbusters', 'fantasy');
INSERT INTO ACTINGEVENTS (name, title,genre) VALUES ('Christian Bale', 'The Dark Knight', 'crime');
INSERT INTO ACTINGEVENTS (name, title,genre) VALUES ('Diane Keaton', 'The Godfather: Part II', 'crime');
INSERT INTO ACTINGEVENTS (name, title,genre) VALUES ('Jennifer Aniston', 'Office Space', 'comedy');
INSERT INTO ACTINGEVENTS (name, title,genre) VALUES ('Judy Garland', 'The Wizard of Oz', 'fantasy');
INSERT INTO ACTINGEVENTS (name, title,genre) VALUES ('Keanu Reeves', 'The Matrix', 'fantasy');
INSERT INTO ACTINGEVENTS (name, title,genre) VALUES ('Laura Dern', 'Jurassic Park', 'fantasy');
INSERT INTO ACTINGEVENTS (name, title,genre) VALUES ('Matt Damon', 'The Martian', 'drama');
INSERT INTO ACTINGEVENTS (name, title,genre) VALUES ('Meryl Streep', 'The Iron Lady', 'drama');
INSERT INTO ACTINGEVENTS (name, title,genre) VALUES ('Russell Crowe', 'Gladiator', 'drama');
INSERT INTO ACTINGEVENTS (name, title,genre) VALUES ('Will Smith', 'Men in Black', 'comedy');

CREATE STREAM actingevents_drama AS
SELECT name, title
FROM ACTINGEVENTS
WHERE genre='drama';

CREATE STREAM actingevents_fantasy AS
SELECT name, title
FROM ACTINGEVENTS
WHERE genre='fantasy';

CREATE STREAM actingevents_other AS
SELECT name, title, genre
FROM ACTINGEVENTS
WHERE genre != 'drama' AND genre != 'fantasy';
