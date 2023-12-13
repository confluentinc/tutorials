SELECT *
    FROM purchases
    EMIT CHANGES
    LIMIT 6;
