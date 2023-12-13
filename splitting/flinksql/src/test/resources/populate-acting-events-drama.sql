INSERT INTO acting_events_drama
    SELECT name, title
    FROM acting_events
    WHERE genre = 'drama';
