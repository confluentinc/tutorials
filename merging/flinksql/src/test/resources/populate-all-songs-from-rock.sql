INSERT INTO all_songs
SELECT
    artist,
    title,
    'rock' AS genre
FROM rock_songs;
