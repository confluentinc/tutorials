INSERT INTO all_songs
SELECT
    artist,
    title,
    'classical' AS genre
FROM classical_songs;
